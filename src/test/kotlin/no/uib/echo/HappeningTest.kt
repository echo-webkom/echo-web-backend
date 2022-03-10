package no.uib.echo

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.uib.echo.plugins.configureRouting
import no.uib.echo.schema.Answer
import no.uib.echo.schema.HAPPENING_TYPE
import no.uib.echo.schema.Happening
import no.uib.echo.schema.HappeningWithSlugJson
import no.uib.echo.schema.Registration
import no.uib.echo.schema.SpotRange
import no.uib.echo.schema.SpotRangeJson
import no.uib.echo.schema.removeSlug
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.util.Base64

class HappeningTest : StringSpec({
    val everyoneSpotRange = listOf(SpotRangeJson(50, 1, 5))
    val exHap: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-med-noen",
            "$type med Noen!",
            "2020-04-29T20:43:29Z",
            "2030-04-29T20:43:29Z",
            everyoneSpotRange,
            type,
            "test@test.com"
        )
    }

    val be = listOf(HAPPENING_TYPE.BEDPRES, HAPPENING_TYPE.EVENT)
    val adminKey = "admin-passord"
    val basicAuth = "Basic ${Base64.getEncoder().encodeToString("admin:$adminKey".toByteArray())}"
    val featureToggles =
        FeatureToggles(sendEmailReg = false, sendEmailHap = false, rateLimit = false, verifyRegs = false)

    beforeSpec { DatabaseHandler(true, URI(System.getenv("DATABASE_URL")), null).init() }
    beforeTest {
        transaction {
            SchemaUtils.drop(
                Happening, Registration, Answer, SpotRange
            )
            SchemaUtils.create(
                Happening, Registration, Answer, SpotRange
            )
        }
    }

    "When trying to submit a happening, server should respond with OK." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val testCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/${exHap(t).slug}") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, basicAuth
                        )
                        setBody(Json.encodeToString(removeSlug(exHap(t))))
                    }

                testCall.response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    "Whe trying to update happening spots, server should respond with OK." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val submitHappeningCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/${exHap(t).slug}") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, basicAuth
                        )
                        setBody(Json.encodeToString(removeSlug(exHap(t))))
                    }

                submitHappeningCall.response.status() shouldBe HttpStatusCode.OK

                val updateHappeningCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/${exHap(t).slug}") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, basicAuth
                        )
                        setBody(
                            Json.encodeToString(
                                removeSlug(exHap(t)).copy(
                                    spotRanges = listOf(
                                        everyoneSpotRange[0].copy(
                                            spots = 123
                                        )
                                    )
                                )
                            )
                        )
                    }

                updateHappeningCall.response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    "When trying to update a happening with the exact same values, server should respond with ACCEPTED." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val submitBedpresCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/${exHap(t).slug}") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, basicAuth
                        )
                        setBody(Json.encodeToString(removeSlug(exHap(t))))
                    }

                submitBedpresCall.response.status() shouldBe HttpStatusCode.OK

                val updateBedpresCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/${exHap(t).slug}") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, basicAuth
                        )
                        setBody(Json.encodeToString(removeSlug(exHap(t))))
                    }

                updateBedpresCall.response.status() shouldBe HttpStatusCode.Accepted
            }
        }
    }

    "When trying to submit a happening with bad data, server should respond with INTERNAL_SERVER_ERROR." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val testCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/${exHap(t).slug}") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, basicAuth
                        )
                        setBody("""{ "spots": 69, "registrationDate": "2021-04-29T20:43:29Z" }""")
                    }

                testCall.response.status() shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    "When trying to submit or update a happening with wrong Authorization header, server should respond with UNAUTHORIZED." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            val wrongAuth = "Basic ${Base64.getEncoder().encodeToString("admin:feil-passord-ass-100".toByteArray())}"

            for (t in be) {
                val testCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/${exHap(t).slug}") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, wrongAuth
                        )
                        setBody(Json.encodeToString(removeSlug(exHap(t))))
                    }

                testCall.response.status() shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    "When trying to delete a happening with wrong Authorization header, server should respond with UNAUTHORIZED." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            val wrongAuth = "Basic ${Base64.getEncoder().encodeToString("admin:feil-passord-ass-100".toByteArray())}"

            for (t in be) {
                val testCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Delete, uri = "/happening/${exHap(t).slug}") {
                        addHeader(
                            HttpHeaders.Authorization, wrongAuth
                        )
                    }

                testCall.response.status() shouldBe HttpStatusCode.Unauthorized
            }
        }
    }
})
