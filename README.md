# Samatra-testing

Samatra-testing is a collection of utilities for testing Samatra controllers or any other servlets in memory, without requiring a running server. 

## Licensing
The MIT License (MIT) http://opensource.org/licenses/MIT
Copyright © 2017 Springer Nature

## Maintenance
Submit issues and PR's to this github.

## Unit test Samarta controllers

### Getting
```
resolvers += "jitpack" at "https://jitpack.io",
libraryDependencies += "com.github.springernature:samatra-testing-unit" % "v1.0"
```

### Usage
You can either match again a HttpResp, or "run" the resp and compare the status code, output, headers, and cookies:

```
whenReady(routes.get("/hello/sam", cookies = Seq(new Cookie("cookie", "expectedValue")))) { result =>
    result shouldBe WithCookies(Seq(AddCookie("cookie", "expectedValue"))) {
      WithHeaders("a" -> "b") {
        StringResp("sam")
      }
    }
}
```
or
```
whenReady(routes.get("/request-response")) { result =>
    val (statusCode, headers, _, body) = result.run()
    
    statusCode shouldBe 200
    headers("Date") shouldBe Seq("Thu, 18 05 2017 12:00:00 GMT")
    new String(body) shouldBe "sam"
  }
}
```
See [ExampleTest]() for further examples.

## Test any servlet/filter without starting a server

### Getting
```
resolvers += "jitpack" at "https://jitpack.io",
libraryDependencies += "com.github.springernature:samatra-testing-asynchttp" % "v1.0"
```

### Usage

Mount any servlet and get back an instance of AsyncHttpClient (2.0.32) to use to make requests (except it's in memory)

```
 val http: AsyncHttpClient = client(new ServerConfig {
    mount("/*", Routes(basic))
    mount("/regex/*", Routes(regex))
    mount("/caching/*", Routes(caching))
    mount("/future/*", Routes(futures))
  })
```

See [ControllerTest]() for further examples. 

You can set up your servlet under test _and_ all of the http services it calls as stubs (or wiremocks), all in memory.
(You need to be careful that the http is passed by name to your servlet, or stackoverflow ensues)

## Don't trust the in memory magic? - start with a jetty backend instead ...

### Getting
```
resolvers += "jitpack" at "https://jitpack.io",
libraryDependencies += "com.github.springernature:samatra-testing-jetty" % "v1.0"
```

### Usage

```
class ControllerTests extends FunSpec with ScalaFutures with RoutesFixtures with BeforeAndAfterAll with JettyBackend {

  val http: AsyncHttpClient = client(new ServerConfig {
    mount("/*", Routes(basic))
    mount("/regex/*", Routes(regex))
    mount("/caching/*", Routes(caching))
    mount("/future/*", Routes(futures))
  })
```

See [ControllerTest]() for more details.

## Wire(less)mock

### Getting
```
resolvers += "jitpack" at "https://jitpack.io",
libraryDependencies += "com.github.springernature:samatra-testing-wiremock" % "v1.0"
```

### Usage

Create an AsyncHttpClient with wiremock backed in. This uses Wiremock servlet to stub/mock http requests, but again all in memory.

```
val wm = new WiremockHelper(wmContextPath = "/wm/*") with InMemoryBacked

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
    
```

See [WiremockTest]() for more details.

## Htmlunitdriver without a server

### Getting
```
resolvers += "jitpack" at "https://jitpack.io",
libraryDependencies += "com.github.springernature:samatra-testing-htmlunitdriver" % "v1.0"
```

### Usage

Create an Htmlunit selenium driver backed by an in memory async http client.

```
val http: AsyncHttpClient = client(new ServerConfig {
  mount("/home/*", Routes(new Controller {
    get("/Hello") { req =>
      s"""<html>
        |<head>
        |  <title>${req.queryStringParamValue("title")}</title>
        |</head>
        |</html>""".stripMargin
    }
  }))
})

val driver: WebDriver = http.driver

driver.get("/home/Hello?title=Hi%20Sam")
driver.getTitle shouldBe "Hi Sam"

```

See [HtmlUnitTest]() for more details.
