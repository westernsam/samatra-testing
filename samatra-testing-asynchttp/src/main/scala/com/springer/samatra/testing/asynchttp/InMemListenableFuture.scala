package com.springer.samatra.testing.asynchttp

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import org.asynchttpclient._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}


class InMemListenableFuture[T](handler: AsyncHandler[T], eventualResponse: Future[Unit])(implicit ec: ExecutionContext) extends ListenableFuture[T] {
  val cancelled = new AtomicBoolean(false)

  override def touch(): Unit = ()
  override def done(): Unit = {
    cancelled.set(true)
    handler.onCompleted()
  }

  override def abort(t: Throwable): Unit = {
    cancel(true)
    handler.onThrowable(t)
  }

  override def toCompletableFuture: CompletableFuture[T] = {
    val value = new CompletableFuture[T]()
    eventualResponse.onComplete(_ => value.complete(get()))
    value
  }

  override def addListener(listener: Runnable, exec: Executor): ListenableFuture[T] = {
    eventualResponse.onComplete {
      _ => if (!cancelled.get) exec.execute(listener)
    }
    this
  }

  override def cancel(mayInterruptIfRunning: Boolean): Boolean = cancelled.getAndSet(!isDone)
  override def isCancelled: Boolean = cancelled.get()
  override def isDone: Boolean = cancelled.get || eventualResponse.isCompleted
  override def get(): T = {
    Await.ready(eventualResponse, Duration.Inf)
    eventualResponse.value match {
      case Some(Success(_)) => handler.onCompleted()
      case Some(Failure(ex)) => handler.onThrowable(ex); throw ex
      case _ => throw new IllegalStateException("Future should be finishde")
    }
  }
  override def get(timeout: Long, unit: TimeUnit): T = {
    Await.ready(eventualResponse, Duration(unit.toMillis(timeout), TimeUnit.MILLISECONDS))
    eventualResponse.value match {
      case Some(Success(_)) => handler.onCompleted()
      case Some(Failure(ex)) => handler.onThrowable(ex); throw ex
      case _ => throw new IllegalStateException("Future should be finishde")
    }
  }
}

