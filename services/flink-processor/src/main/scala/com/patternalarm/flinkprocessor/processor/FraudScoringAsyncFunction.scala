package com.patternalarm.flinkprocessor.processor

import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.{AsyncFunction, ResultFuture}
import sttp.client3._

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FraudScoringAsyncFunction(fastapiUrl: String)
    extends AsyncFunction[TimedWindowAggregate, (TimedWindowAggregate, PredictResponse)] {

  @transient private var backend: SttpBackend[Future, Any] = _
  @transient private var ec: ExecutionContext = _ // ← Removed implicit

  def open(parameters: Configuration): Unit = {
    backend = HttpClientFutureBackend()
    ec = scala.concurrent.ExecutionContext.global
  }

  def close(): Unit =
    if (backend != null) {
      backend.close()
    }

  override def asyncInvoke(
    aggregate: TimedWindowAggregate,
    resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
  ): Unit = {

    val request = PredictRequest.fromAggregate(aggregate)
    val requestJson = JsonUtils.toJson(request)

    val responseFuture: Future[Response[String]] =
      basicRequest
        .post(uri"$fastapiUrl/predict")
        .contentType("application/json")
        .body(requestJson)
        .response(asStringAlways)
        .send(backend)

    // Use explicit ec instead of implicit
    responseFuture.onComplete {
      case Success(response) =>
        if (response.code.isSuccess) {
          try {
            val predictResponse = JsonUtils.fromJson[PredictResponse](response.body)
            resultFuture.complete(Iterable((aggregate, predictResponse)).asJavaCollection)
          } catch {
            case e: Exception =>
              resultFuture.completeExceptionally(
                new RuntimeException(s"Failed to parse response: ${e.getMessage}", e)
              )
          }
        } else {
          resultFuture.completeExceptionally(
            new RuntimeException(s"FastAPI error ${response.code}: ${response.body}")
          )
        }

      case Failure(exception) =>
        resultFuture.completeExceptionally(
          new RuntimeException(s"HTTP call failed: ${exception.getMessage}", exception)
        )
    }(ec) // ← Explicit ec here
  }

  override def timeout(
    input: TimedWindowAggregate,
    resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
  ): Unit =
    resultFuture.completeExceptionally(
      new RuntimeException(s"Timeout for actor ${input.actorId}")
    )
}
