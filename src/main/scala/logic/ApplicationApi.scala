package logic

import java.util.UUID

import boilerplate.Bridge
import boilerplate.Bridge._
import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import domain.{ NoCustomer, _ }
import interfaces.Customers._
import interfaces.Products._
import interfaces._
import logic.CustomerEmails._
import logic.Purchases._

import scala.language.higherKinds

class ApplicationApi[F[_]: Monad: Purchases: Customers: Products, H[_]: Monad: CustomerEmails, R[_]: Monad](implicit fr: Bridge[F, R], hr: Bridge[H, R]) {

  def findProduct(customerId: UUID, name: String): R[Either[NoCustomer.type, Seq[Product]]] = {
    type resultType = Either[NoCustomer.type, Seq[Product]]
    //combine as the call is from one domain
    val result: F[resultType] = getCustomer(customerId).flatMap {
      case Some(_) => findProductByName(name).map(Right(_))
      case None    => Monad[F].pure(Left(NoCustomer))
    }

    //translate combination
    translate(result): R[resultType]
  }

  def makePurchase(customerId: UUID, productId: UUID): R[Either[Seq[DomainError], Seq[EmailType]]] = {
    //explicit type declaration for convenience
    type purchaseType = Either[Seq[DomainError], (Customer, Product)]
    type emailType = Either[Seq[DomainError], Seq[EmailType]]
    type resultType = Either[Seq[DomainError], Seq[EmailType]]

    //call what you want from different domains
    val purchase: F[purchaseType] = purchaseProduct(customerId, productId)
    val send: Customer => H[emailType] = c => sendPurchaseEmail(c).map(emails => Right(emails))

    //combine
    val result: R[resultType] = translate(purchase).flatMap {
      case Right((c, _)) => translate(send(c))
      case Left(e)       => Monad[R].pure(Left(e): resultType)
    }

    result
  }
}