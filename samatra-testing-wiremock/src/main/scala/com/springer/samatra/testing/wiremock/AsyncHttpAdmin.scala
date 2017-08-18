package com.springer.samatra.testing.wiremock

import java.net.HttpURLConnection.{HTTP_NO_CONTENT, HTTP_OK}
import java.util.UUID

import com.github.tomakehurst.wiremock.admin.model._
import com.github.tomakehurst.wiremock.admin.tasks._
import com.github.tomakehurst.wiremock.admin.{AdminRoutes, AdminTask, RequestSpec}
import com.github.tomakehurst.wiremock.client.VerificationException
import com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked
import com.github.tomakehurst.wiremock.common.{AdminException, Json}
import com.github.tomakehurst.wiremock.core.Admin
import com.github.tomakehurst.wiremock.global.GlobalSettings
import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.verification.{FindNearMissesResult, FindRequestsResult, LoggedRequest, VerificationResult}
import com.google.common.base.Charsets.UTF_8
import com.google.common.base.Preconditions.checkNotNull
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpUriRequest, RequestBuilder}
import org.apache.http.entity.StringEntity
import org.asynchttpclient.AsyncHttpClient

object AsyncHttpAdmin {
  private val ADMIN_URL_PREFIX = "%s/__admin"
  private def jsonStringEntity(json: String) = new StringEntity(json, UTF_8.name)
}

class AsyncHttpAdmin(httpClient: => AsyncHttpClient, val urlPathPrefix: String) extends Admin {
  final private val adminRoutes = AdminRoutes.defaults

  def this(client: => AsyncHttpClient) {
    this(client, "")
  }
  override def addStubMapping(stubMapping: StubMapping): Unit = {
    if (stubMapping.getRequest.hasCustomMatcher) throw new AdminException("Custom matchers can't be used when administering a remote WireMock server. " + "Use WireMockRule.stubFor() or WireMockServer.stubFor() to administer the local instance.")
    val _ = executeRequest(adminRoutes.requestSpecForTask(classOf[CreateStubMappingTask]), PathParams.empty, stubMapping, classOf[Void], 201)

  }
  override def editStubMapping(stubMapping: StubMapping): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[OldEditStubMappingTask]), Json.write(stubMapping), HTTP_NO_CONTENT)
  }
  override def removeStubMapping(stubbMapping: StubMapping): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[OldRemoveStubMappingTask]), Json.write(stubbMapping), HTTP_OK)
  }
  override def listAllStubMappings: ListStubMappingsResult = executeRequest(adminRoutes.requestSpecForTask(classOf[GetAllStubMappingsTask]), classOf[ListStubMappingsResult])

  @SuppressWarnings(Array("unchecked")) override def getStubMapping(id: UUID): SingleStubMappingResult = executeRequest(adminRoutes.requestSpecForTask(classOf[GetStubMappingTask]), PathParams.single("id", id), classOf[SingleStubMappingResult])
  override def saveMappings(): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[SaveMappingsTask]), null, HTTP_OK)
  }
  override def resetAll(): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[ResetTask]), null, HTTP_OK)
  }
  override def resetRequests(): Unit = {
    executeRequest(adminRoutes.requestSpecForTask(classOf[ResetRequestsTask]))
  }
  override def resetScenarios(): Unit = {
    executeRequest(adminRoutes.requestSpecForTask(classOf[ResetScenariosTask]))
  }
  override def resetMappings(): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[ResetStubMappingsTask]), null, HTTP_OK)
  }
  override def resetToDefaultMappings(): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[ResetToDefaultMappingsTask]), null, HTTP_OK)
  }

  override def getServeEvents: GetServeEventsResult = executeRequest(adminRoutes.requestSpecForTask(classOf[GetAllRequestsTask]), classOf[GetServeEventsResult])

  override def getServedStub(id: UUID): SingleServedStubResult = executeRequest(adminRoutes.requestSpecForTask(classOf[GetServedStubTask]), PathParams.single("id", id), classOf[SingleServedStubResult])

  override def countRequestsMatching(requestPattern: RequestPattern): VerificationResult = {
    val body = postJsonAssertOkAndReturnBody(urlFor(classOf[GetRequestCountTask]), Json.write(requestPattern), HTTP_OK)
    VerificationResult.from(body)
  }
  override def findRequestsMatching(requestPattern: RequestPattern): FindRequestsResult = {
    val body = postJsonAssertOkAndReturnBody(urlFor(classOf[FindRequestsTask]), Json.write(requestPattern), HTTP_OK)
    Json.read(body, classOf[FindRequestsResult])
  }
  override def findUnmatchedRequests: FindRequestsResult = {
    val body = getJsonAssertOkAndReturnBody(urlFor(classOf[FindUnmatchedRequestsTask]), HTTP_OK)
    Json.read(body, classOf[FindRequestsResult])
  }
  override def findNearMissesForUnmatchedRequests: FindNearMissesResult = {
    val body = getJsonAssertOkAndReturnBody(urlFor(classOf[FindNearMissesForUnmatchedTask]), HTTP_OK)
    Json.read(body, classOf[FindNearMissesResult])
  }
  override def findTopNearMissesFor(loggedRequest: LoggedRequest): FindNearMissesResult = {
    val body = postJsonAssertOkAndReturnBody(urlFor(classOf[FindNearMissesForRequestTask]), Json.write(loggedRequest), HTTP_OK)
    Json.read(body, classOf[FindNearMissesResult])
  }
  override def findTopNearMissesFor(requestPattern: RequestPattern): FindNearMissesResult = {
    val body = postJsonAssertOkAndReturnBody(urlFor(classOf[FindNearMissesForRequestPatternTask]), Json.write(requestPattern), HTTP_OK)
    Json.read(body, classOf[FindNearMissesResult])
  }
  override def updateGlobalSettings(settings: GlobalSettings): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[GlobalSettingsUpdateTask]), Json.write(settings), HTTP_OK)
  }
  override def shutdownServer(): Unit = {
    postJsonAssertOkAndReturnBody(urlFor(classOf[ShutdownServerTask]), null, HTTP_OK)
  }
  private def postJsonAssertOkAndReturnBody(url: String, json: String, expectedStatus: Int) = {
    val post = new HttpPost(url)
    if (json != null) post.setEntity(AsyncHttpAdmin.jsonStringEntity(json))
    safelyExecuteRequest(url, expectedStatus, post)
  }
  protected def getJsonAssertOkAndReturnBody(url: String, expectedStatus: Int): String = {
    val get = new HttpGet(url)
    safelyExecuteRequest(url, expectedStatus, get)
  }
  private def executeRequest(requestSpec: RequestSpec): Unit = {
    val _ = executeRequest(requestSpec, PathParams.empty, null, classOf[Void], 200)
  }

  private def executeRequest[B, R](requestSpec: RequestSpec, responseType: Class[R]): R = executeRequest(requestSpec, PathParams.empty, null, responseType, 200)

  private def executeRequest[B, R](requestSpec: RequestSpec, pathParams: PathParams, responseType: Class[R]) : R = executeRequest(requestSpec, pathParams, null, responseType, 200)

  private def executeRequest[B, R](requestSpec: RequestSpec, pathParams: PathParams, requestBody: B, responseType: Class[R], expectedStatus: Int): R = {
    val url = String.format(AsyncHttpAdmin.ADMIN_URL_PREFIX + requestSpec.path(pathParams), urlPathPrefix)
    val requestBuilder = RequestBuilder.create(requestSpec.method.getName).setUri(url)
    if (requestBody != null)
      requestBuilder.setEntity(AsyncHttpAdmin.jsonStringEntity(Json.write(requestBody)))

    val responseBodyString = safelyExecuteRequest(url, expectedStatus, requestBuilder.build)

    if (responseType eq classOf[Void]) null.asInstanceOf[R]
    else Json.read(responseBodyString, responseType)
  }

  private def safelyExecuteRequest(url: String, expectedStatus: Int, request: HttpUriRequest) = try {
    var boundRequestBuilder = prepareRequest(request)
    request match {
      case request1: HttpEntityEnclosingRequest => boundRequestBuilder = boundRequestBuilder.setBody(request1.getEntity.getContent)
      case _ =>
    }
    val response = boundRequestBuilder.execute.get
    val statusCode = response.getStatusCode
    if (statusCode != expectedStatus) throw new VerificationException("Expected status " + expectedStatus + " for " + url + " but was " + statusCode)
    response.getResponseBody
  } catch {
    case e: Exception =>
      throwUnchecked(e, classOf[String])
  }

  private def prepareRequest(request: HttpUriRequest) = request.getMethod match {
    case "GET" =>
      httpClient.prepareGet(request.getURI.toString)
    case "POST" =>
      httpClient.preparePost(request.getURI.toString)
    case "PUT" =>
      httpClient.preparePut(request.getURI.toString)
    case "DELETE" =>
      httpClient.prepareDelete(request.getURI.toString)
    case _ =>
      throw new RuntimeException(String.format("Unknown http method %s", request.getMethod))
  }

  private def urlFor(taskClass: Class[_ <: AdminTask]) = {
    val requestSpec = adminRoutes.requestSpecForTask(taskClass)
    checkNotNull(requestSpec)
    String.format(AsyncHttpAdmin.ADMIN_URL_PREFIX + requestSpec.path, urlPathPrefix)
  }
}

