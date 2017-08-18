package com.springer.samatra.testing.wiremock

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, urlEqualTo}
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.springer.samatra.testing.asynchttp.{InMemoryBackend, JettyBacked}
import org.scalatest.FunSpec
import org.scalatest.Matchers._

class WiremockTest extends FunSpec {

  it("should mock without wires") {
    test(new WiremockHelper(wmContextPath = "/wm/*") with InMemoryBackend)
  }

  it("should mock with wires") {
    test(new WiremockHelper(wmContextPath = "/wm/*") with JettyBacked)
  }


  private def test(wm: WiremockHelper) = {
    wm.wireMock.register(
      WireMock.post(urlEqualTo("/wm/something"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("hello")
        )
    )

    val response = wm.http.preparePost("/wm/something").setBody("Hello").execute().get()

    response.getStatusCode shouldBe 200
    response.getResponseBody shouldBe "hello"

    wm.wireMock.verifyThat(
      WireMock.postRequestedFor(urlEqualTo("/wm/something")).withRequestBody(new EqualToPattern("Hello"))
    )
  }
}