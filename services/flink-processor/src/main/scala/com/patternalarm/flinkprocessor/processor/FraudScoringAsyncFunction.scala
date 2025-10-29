package com.patternalarm.flinkprocessor.processor

import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FraudScoringAsyncFunction(fastapiUrl: String)
  extends RichAsyncFunction[TimedWindowAggregate, (TimedWindowAggregate, PredictResponse)] {

  @transient private var backend: SttpBackend[Future, Any] = _
  @transient private var ec: ExecutionContext = _

  override def open(parameters: Configuration): Unit = {
    println(s"🔌 Initializing FraudScoringAsyncFunction...")
    println(s"🔌 FastAPI URL: $fastapiUrl")
    println(s"🔌 BUILD VERSION: AsyncHttpClient v3.9.0 - FINAL-2025-10-29-03:35")

    backend = AsyncHttpClientFutureBackend()  // ← Different backend
    ec = scala.concurrent.ExecutionContext.global
    println(s"✅ HTTP client backend initialized (AsyncHttpClient)")
  }

  override def close(): Unit = {
    println("🔌 Closing HTTP client backend...")
    if (backend != null) {
      backend.close()
      println("✅ HTTP client closed")
    }
  }

  override def asyncInvoke(
                            aggregate: TimedWindowAggregate,
                            resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
                          ): Unit = {

    val timestamp = System.currentTimeMillis()
    println(s"📤 [$timestamp] Preparing ML prediction for actor=${aggregate.actorId}")

    val request = PredictRequest.fromAggregate(aggregate)
    val requestJson = JsonUtils.toJsonCamelCase(request)

    println(s"📤 [$timestamp] JSON size: ${requestJson.length} chars")
    println(s"📤 [$timestamp] Target: $fastapiUrl/predict")
    println(s"📤 [$timestamp] Creating HTTP Future with OkHttp v3.9.0 - FINAL-2025-10-29-03:05...")

//    val responseFuture: Future[Response[String]] =
//      basicRequest
//        .post(uri"$fastapiUrl/predict")
//        .contentType("application/json")
//        .body(requestJson)
//        .response(asStringAlways)
//        .send(backend)

    println(s"📤 [$timestamp] Step 1: Creating basicRequest...")
    val req1 = basicRequest

    println(s"📤 [$timestamp] Step 2: Setting POST method...")
    val req2 = req1.post(uri"$fastapiUrl/predict")

    println(s"📤 [$timestamp] Step 3: Setting content-type...")
    val req3 = req2.contentType("application/json")

    println(s"📤 [$timestamp] Step 4: Setting body (${requestJson.length} chars)...")
    val req4 = req3.body(requestJson)

    println(s"📤 [$timestamp] Step 5: Setting response handler...")
    val req5 = req4.response(asStringAlways)

    println(s"📤 [$timestamp] Step 6: Calling send(backend)...")
    val responseFuture = req5.send(backend)


    println(s"📤 [$timestamp] Future created, attempting to attach callback...")

    try {
      import scala.concurrent.ExecutionContext.Implicits.global

      responseFuture.onComplete {
        case Success(response) =>
          val callbackTime = System.currentTimeMillis()
          println(s"✅ [$callbackTime] CALLBACK FIRED! actor=${aggregate.actorId}, code=${response.code}, elapsed=${callbackTime - timestamp}ms")

          if (response.code.isSuccess) {
            try {
              val predictResponse = JsonUtils.fromJson[PredictResponse](response.body)
              println(s"✅ [$callbackTime] Parsed: fraud_score=${predictResponse.fraudScore}, model=${predictResponse.modelVersion}")
              resultFuture.complete(Iterable((aggregate, predictResponse)).asJavaCollection)
            } catch {
              case e: Exception =>
                System.err.println(s"❌ [$callbackTime] Parse failed for actor=${aggregate.actorId}")
                System.err.println(s"❌ Response body: ${response.body.take(500)}")
                System.err.println(s"❌ Error: ${e.getMessage}")
                e.printStackTrace()
                resultFuture.completeExceptionally(
                  new RuntimeException(s"Failed to parse response: ${e.getMessage}", e)
                )
            }
          } else {
            System.err.println(s"❌ [$callbackTime] API error ${response.code} for actor=${aggregate.actorId}")
            System.err.println(s"❌ Body: ${response.body}")
            resultFuture.completeExceptionally(
              new RuntimeException(s"FastAPI error ${response.code}: ${response.body}")
            )
          }

        case Failure(exception) =>
          val failTime = System.currentTimeMillis()
          System.err.println(s"❌ [$failTime] HTTP FAILURE for actor=${aggregate.actorId}, elapsed=${failTime - timestamp}ms")
          System.err.println(s"❌ URL: $fastapiUrl/predict")
          System.err.println(s"❌ Exception: ${exception.getClass.getName}")
          System.err.println(s"❌ Message: ${exception.getMessage}")
          exception.printStackTrace()
          resultFuture.completeExceptionally(
            new RuntimeException(s"HTTP call failed: ${exception.getMessage}", exception)
          )
      }

      println(s"📤 [$timestamp] Callback attached successfully!")

    } catch {
      case e: Exception =>
        System.err.println(s"❌ [$timestamp] EXCEPTION while attaching callback!")
        System.err.println(s"❌ Exception type: ${e.getClass.getName}")
        System.err.println(s"❌ Exception message: ${e.getMessage}")
        e.printStackTrace()
        resultFuture.completeExceptionally(
          new RuntimeException(s"Failed to attach callback: ${e.getMessage}", e)
        )
    }
  }

  override def timeout(
                        input: TimedWindowAggregate,
                        resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
                      ): Unit = {
    val timeoutTime = System.currentTimeMillis()
    System.err.println(s"⏱️  [$timeoutTime] TIMEOUT TRIGGERED for actor=${input.actorId}")
    System.err.println(s"⏱️  URL: $fastapiUrl/predict")
    resultFuture.completeExceptionally(
      new RuntimeException(s"Timeout for actor ${input.actorId}")
    )
  }
}