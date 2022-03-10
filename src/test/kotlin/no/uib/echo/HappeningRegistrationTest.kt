package no.uib.echo

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.uib.echo.plugins.configureRouting
import no.uib.echo.schema.Answer
import no.uib.echo.schema.AnswerJson
import no.uib.echo.schema.Degree
import no.uib.echo.schema.HAPPENING_TYPE
import no.uib.echo.schema.Happening
import no.uib.echo.schema.HappeningInfoJson
import no.uib.echo.schema.HappeningWithSlugJson
import no.uib.echo.schema.Registration
import no.uib.echo.schema.RegistrationJson
import no.uib.echo.schema.SpotRange
import no.uib.echo.schema.SpotRangeJson
import no.uib.echo.schema.bachelors
import no.uib.echo.schema.insertOrUpdateHappening
import no.uib.echo.schema.masters
import no.uib.echo.schema.removeSlug
import no.uib.echo.schema.toCsv
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.util.Base64

class HappeningRegistrationTest : StringSpec({
    val everyoneSpotRange = listOf(SpotRangeJson(50, 1, 5))
    val oneTwoSpotRange = listOf(SpotRangeJson(50, 1, 2))
    val threeFiveSpotRange = listOf(SpotRangeJson(50, 3, 5))
    val everyoneSplitSpotRange = listOf(
        SpotRangeJson(20, 1, 2), SpotRangeJson(20, 3, 5)
    )
    val everyoneInfiniteSpotRange = listOf(SpotRangeJson(0, 1, 5))
    val onlyOneSpotRange = listOf(SpotRangeJson(1, 1, 5))
    val fewSpotRange = listOf(SpotRangeJson(5, 1, 5))

    val hap1: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
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
    val hap2: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-med-noen-andre",
            "$type med Noen Andre!",
            "2019-07-29T20:10:11Z",
            "2030-07-29T20:10:11Z",
            everyoneSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap3: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-dritlang-i-fremtiden",
            "$type dritlangt i fremtiden!!",
            "2037-07-29T20:10:11Z",
            "2038-01-01T20:10:11Z",
            everyoneSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap4: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-for-bare-1-til-2",
            "$type (for bare 1 til 2)!",
            "2020-05-29T20:00:11Z",
            "2030-05-29T20:00:11Z",
            oneTwoSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap5: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-for-bare-3-til-5",
            "$type (for bare 3 til 5)!",
            "2020-06-29T18:07:31Z",
            "2030-06-29T18:07:31Z",
            threeFiveSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap6: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-som-er-splitta-ty-bedkom",
            "$type (som er splitta ty Bedkom)!",
            "2020-06-29T18:07:31Z",
            "2030-06-29T18:07:31Z",
            everyoneSplitSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap7: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-med-uendelig-plasser",
            "$type med uendelig plasser!",
            "2020-06-29T18:07:31Z",
            "2030-06-29T18:07:31Z",
            everyoneInfiniteSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap8: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-med-en-plass",
            "$type med én plass!",
            "2020-06-29T18:07:31Z",
            "2030-06-29T18:07:31Z",
            onlyOneSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap9: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-med-få-plasser",
            "$type med få plasser!",
            "2020-02-18T16:27:05Z",
            "2030-02-18T16:27:05Z",
            fewSpotRange,
            type,
            "test@test.com"
        )
    }
    val hap10: (type: HAPPENING_TYPE) -> HappeningWithSlugJson = { type ->
        HappeningWithSlugJson(
            "$type-som-har-vært",
            "$type som har vært!",
            "2020-02-14T12:00:00Z",
            "2020-02-28T16:15:00Z",
            fewSpotRange,
            type,
            "test@test.com"
        )
    }

    val exReg: (type: HAPPENING_TYPE) -> RegistrationJson = { type ->
        RegistrationJson(
            "tEsT1$type@TeSt.com", "Én", "Navnesen", Degree.DTEK, 3, true, null, false,
            listOf(
                AnswerJson("Skal du ha mat?", "Nei"), AnswerJson("Har du noen allergier?", "Ja masse allergier ass 100")
            ),
            type, null
        )
    }

    val be = listOf(HAPPENING_TYPE.BEDPRES, HAPPENING_TYPE.EVENT)
    val adminKey = "admin-passord"
    val basicAuth = "Basic ${Base64.getEncoder().encodeToString("admin:$adminKey".toByteArray())}"
    val featureToggles =
        FeatureToggles(sendEmailReg = false, sendEmailHap = false, rateLimit = false, verifyRegs = false)

    beforeSpec {
        DatabaseHandler(true, URI(System.getenv("DATABASE_URL")), null).init()
        transaction {
            SchemaUtils.drop(
                Happening, Registration, Answer, SpotRange
            )
            SchemaUtils.create(
                Happening, Registration, Answer, SpotRange
            )
        }
        for (t in be) {
            withContext(Dispatchers.IO) {
                insertOrUpdateHappening(removeSlug(hap1(t)), hap1(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap2(t)), hap2(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap3(t)), hap3(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap4(t)), hap4(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap5(t)), hap5(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap6(t)), hap6(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap7(t)), hap7(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap8(t)), hap8(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap9(t)), hap9(t).slug, null, sendEmail = false, dev = true)
                insertOrUpdateHappening(removeSlug(hap10(t)), hap10(t).slug, null, sendEmail = false, dev = true)
            }
        }
    }
    beforeTest {
        transaction {
            SchemaUtils.drop(
                Registration, Answer
            )
            SchemaUtils.create(
                Registration, Answer
            )
        }
    }

    "Registrations with valid data should submit correctly." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            fun submitReg(degree: Degree, degreeYear: Int, type: HAPPENING_TYPE) {
                val submitRegCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(type).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(
                            Json.encodeToString(
                                exReg(type).copy(
                                    degree = degree,
                                    degreeYear = degreeYear,
                                    email = "${type}test${degree}$degreeYear@test.com"
                                )
                            )
                        )
                    }

                submitRegCall.response.status() shouldBe HttpStatusCode.OK
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.OK
            }

            for (t in be) {
                for (b in bachelors) {
                    for (y in 1..3) {
                        submitReg(b, y, t)
                    }
                }

                for (m in masters) {
                    for (y in 4..5) {
                        submitReg(m, y, t)
                    }
                }

                submitReg(Degree.KOGNI, 3, t)
                submitReg(Degree.ARMNINF, 1, t)
            }
        }
    }

    "The same user should be able to sign up for two different happenings." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                for (slug in listOf(hap1(t).slug, hap2(t).slug)) {
                    val submitRegCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/$slug/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(Json.encodeToString(exReg(t)))
                        }

                    submitRegCall.response.status() shouldBe HttpStatusCode.OK
                    val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.OK
                }
            }
        }
    }

    "Registration with valid data and empty question list should submit correctly." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val submitRegCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t).copy(answers = emptyList())))
                    }
                submitRegCall.response.status() shouldBe HttpStatusCode.OK
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.OK
            }
        }
    }

    "You should not be able to sign up for a happening more than once." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val submitRegCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t)))
                    }

                submitRegCall.response.status() shouldBe HttpStatusCode.OK
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.OK

                val submitRegAgainCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t)))
                    }

                submitRegAgainCall.response.status() shouldBe HttpStatusCode.UnprocessableEntity
                val resAgain = Json.decodeFromString<RegistrationResponseJson>(submitRegAgainCall.response.content!!)

                resAgain shouldNotBe null
                resAgain.code shouldBe RegistrationResponse.AlreadySubmitted
            }
        }
    }

    "You should not be able to sign up for a happening more than once (wait list)." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val fillUpRegsCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap8(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t)))
                    }

                fillUpRegsCall.response.status() shouldBe HttpStatusCode.OK
                val fillUpRes = Json.decodeFromString<RegistrationResponseJson>(fillUpRegsCall.response.content!!)

                fillUpRes shouldNotBe null
                fillUpRes.code shouldBe RegistrationResponse.OK

                val newEmail = "bruh@moment.com"
                val submitRegCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap8(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(
                            Json.encodeToString(
                                exReg(t).copy(
                                    email = newEmail
                                )
                            )
                        )
                    }

                submitRegCall.response.status() shouldBe HttpStatusCode.Accepted
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.WaitList

                val submitRegAgainCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap8(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(
                            Json.encodeToString(
                                exReg(t).copy(
                                    email = newEmail
                                )
                            )
                        )
                    }

                submitRegAgainCall.response.status() shouldBe HttpStatusCode.UnprocessableEntity
                val resAgain = Json.decodeFromString<RegistrationResponseJson>(submitRegAgainCall.response.content!!)

                resAgain shouldNotBe null
                resAgain.code shouldBe RegistrationResponse.AlreadySubmittedWaitList
            }
        }
    }

    "You should not be able to sign up for a happening before the registration date." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val submitRegCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap3(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t)))
                    }

                submitRegCall.response.status() shouldBe HttpStatusCode.Forbidden
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.TooEarly
            }
        }
    }

    "You should not be able to sign up for a happening after the happening date." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val submitRegCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap10(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t)))
                    }

                submitRegCall.response.status() shouldBe HttpStatusCode.Forbidden
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.TooLate
            }
        }
    }

    "You should not be able to sign up for a happening that doesn't exist." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val submitRegCall: TestApplicationCall = handleRequest(
                    method = HttpMethod.Post,
                    uri = "/happening/ikke-eksisterende-happening-som-ikke-finnes/registrations"
                ) {
                    addHeader(HttpHeaders.ContentType, "application/json")
                    setBody(Json.encodeToString(exReg(t)))
                }

                submitRegCall.response.status() shouldBe HttpStatusCode.Conflict
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.HappeningDoesntExist
            }
        }
    }

    "Email should be valid." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            val emails = listOf(
                Pair("test@test", false),
                Pair("test@test.", false),
                Pair("test_test.com", false),
                Pair("@test.com", false),
                Pair("test@uib.no", true),
                Pair("test@student.uib.no", true),
                Pair("test_person@hotmail.com", true),
                Pair("ola.nordmann@echo.uib.no", true)
            )

            for (t in be) {
                for ((email, isValid) in emails) {
                    val testCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(Json.encodeToString(exReg(t).copy(email = email)))
                        }

                    if (isValid) testCall.response.status() shouldBe HttpStatusCode.OK
                    else testCall.response.status() shouldBe HttpStatusCode.BadRequest

                    val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)
                    res shouldNotBe null

                    if (isValid) res.code shouldBe RegistrationResponse.OK
                    else res.code shouldBe RegistrationResponse.InvalidEmail
                }
            }
        }
    }

    "Degree year should not be smaller than one." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val testCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t).copy(degreeYear = 0)))
                    }

                testCall.response.status() shouldBe HttpStatusCode.BadRequest
                val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.InvalidDegreeYear
            }
        }
    }

    "Degree year should not be bigger than five." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val testCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t).copy(degreeYear = 6)))
                    }

                testCall.response.status() shouldBe HttpStatusCode.BadRequest
                val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.InvalidDegreeYear
            }
        }
    }

    "If the degree year is either four or five, the degree should not correspond to a bachelors degree." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            listOf(
                Degree.DTEK,
                Degree.DSIK,
                Degree.DVIT,
                Degree.BINF,
                Degree.IMO,
                Degree.IKT,
            ).map { deg ->
                for (t in be) {
                    for (year in 4..5) {
                        val testCall: TestApplicationCall =
                            handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                                addHeader(HttpHeaders.ContentType, "application/json")
                                setBody(Json.encodeToString(exReg(t).copy(degreeYear = year, degree = deg)))
                            }

                        testCall.response.status() shouldBe HttpStatusCode.BadRequest
                        val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)

                        res shouldNotBe null
                        res.code shouldBe RegistrationResponse.DegreeMismatchBachelor
                    }
                }
            }
        }
    }

    "If the degree year is between one and three, the degree should not correspond to a masters degree." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            listOf(Degree.INF, Degree.PROG).map { deg ->
                for (t in be) {
                    for (i in 1..3) {
                        val testCall: TestApplicationCall =
                            handleRequest(method = HttpMethod.Post, uri = "/happening/(${hap1(t).slug}/registrations") {
                                addHeader(HttpHeaders.ContentType, "application/json")
                                setBody(Json.encodeToString(exReg(t).copy(degreeYear = i, degree = deg)))
                            }

                        testCall.response.status() shouldBe HttpStatusCode.BadRequest
                        val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)

                        res shouldNotBe null
                        res.code shouldBe RegistrationResponse.DegreeMismatchMaster
                    }
                }
            }
        }
    }

    "If degree is KOGNI, degree year should be equal to three." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                for (i in listOf(1, 2, 4, 5)) {
                    val testCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(Json.encodeToString(exReg(t).copy(degree = Degree.KOGNI, degreeYear = i)))
                        }

                    testCall.response.status() shouldBe HttpStatusCode.BadRequest
                    val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.DegreeMismatchKogni
                }
            }
        }
    }

    "If degree is ARMNINF, degree year should be equal to one." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                for (i in 2..5) {
                    val testCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(Json.encodeToString(exReg(t).copy(degree = Degree.ARMNINF, degreeYear = i)))
                        }

                    testCall.response.status() shouldBe HttpStatusCode.BadRequest
                    val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.DegreeMismatchArmninf
                }
            }
        }
    }

    "Terms should be accepted." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val testCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t).copy(terms = false)))
                    }

                testCall.response.status() shouldBe HttpStatusCode.BadRequest
                val res = Json.decodeFromString<RegistrationResponseJson>(testCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.InvalidTerms
            }
        }
    }

    "If a happening has filled up every spot, a registration should be put on the wait list." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                for (i in 1..(hap1(t).spotRanges[0].spots)) {
                    val submitRegCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(Json.encodeToString(exReg(t).copy(email = "tesadasdt$i@test.com")))
                        }

                    submitRegCall.response.status() shouldBe HttpStatusCode.OK
                    val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.OK
                }

                for (i in 1..3) {
                    val submitRegWaitListCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(Json.encodeToString(exReg(t).copy(email = "takadhasdh$i@test.com")))
                        }

                    submitRegWaitListCall.response.status() shouldBe HttpStatusCode.Accepted
                    val res = Json.decodeFromString<RegistrationResponseJson>(submitRegWaitListCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.WaitList
                }
            }
        }
    }

    "You should not be able to sign up for a happening if you are not inside the degree year range." {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                for (i in 1..2) {
                    val submitRegCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap5(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(
                                Json.encodeToString(
                                    exReg(t).copy(
                                        email = "teasds${i}t3t@test.com",
                                        degreeYear = i,
                                    )
                                )
                            )
                        }

                    submitRegCall.response.status() shouldBe HttpStatusCode.Forbidden
                    val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.NotInRange
                }

                for (i in 3..5) {
                    val submitRegCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap4(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(
                                Json.encodeToString(
                                    exReg(t).copy(
                                        email = "tlsbreh100aasdlo0${i}t3t@test.com",
                                        degreeYear = i,
                                        degree = if (i > 3) Degree.INF else Degree.DTEK
                                    )
                                )
                            )
                        }

                    submitRegCall.response.status() shouldBe HttpStatusCode.Forbidden
                    val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.NotInRange
                }
            }
        }
    }

    "Should get correct count of registrations and wait list registrations, and produce correct CSV list" {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            val waitListCount = 10

            for (t in be) {
                val newSlug = "auto-link-test-100-$t"

                val submitHappeningCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Put, uri = "/happening/$newSlug") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        addHeader(
                            HttpHeaders.Authorization, basicAuth
                        )
                        setBody(Json.encodeToString(removeSlug(hap6(t).copy(slug = newSlug))))
                    }

                submitHappeningCall.response.status() shouldBe HttpStatusCode.OK

                val regsList = mutableListOf<RegistrationJson>()

                for (sr in hap6(t).spotRanges) {
                    for (i in 1..(sr.spots + waitListCount)) {
                        val submitRegCall: TestApplicationCall =
                            handleRequest(method = HttpMethod.Post, uri = "/happening/$newSlug/registrations") {
                                addHeader(HttpHeaders.ContentType, "application/json")
                                val newReg = exReg(t).copy(
                                    email = "$t${sr.minDegreeYear}${sr.maxDegreeYear}mIxEdcAsE$i@test.com",
                                    degree = if (sr.maxDegreeYear > 3) Degree.PROG else Degree.DTEK,
                                    degreeYear = if (sr.maxDegreeYear > 3) 4 else 2,
                                    waitList = i > sr.spots
                                )
                                regsList.add(newReg.copy(email = newReg.email.lowercase()))
                                setBody(Json.encodeToString(newReg))
                            }

                        if (i > sr.spots) {
                            submitRegCall.response.status() shouldBe HttpStatusCode.Accepted
                            val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                            res shouldNotBe null
                            res.code shouldBe RegistrationResponse.WaitList
                        } else {
                            submitRegCall.response.status() shouldBe HttpStatusCode.OK
                            val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                            res shouldNotBe null
                            res.code shouldBe RegistrationResponse.OK
                        }
                    }
                }

                val getHappeningInfoCall: TestApplicationCall = handleRequest(
                    method = HttpMethod.Get, uri = "/happening/$newSlug"
                ) {
                    addHeader(
                        HttpHeaders.Authorization, basicAuth
                    )
                }

                getHappeningInfoCall.response.status() shouldBe HttpStatusCode.OK
                val happeningInfo = Json.decodeFromString<HappeningInfoJson>(
                    getHappeningInfoCall.response.content!!,
                )

                for (i in happeningInfo.spotRanges.indices) {
                    happeningInfo.spotRanges[i].regCount shouldBe hap6(t).spotRanges[i].spots
                    happeningInfo.spotRanges[i].waitListCount shouldBe waitListCount
                }

                val getRegistrationsListCall = handleRequest(
                    method = HttpMethod.Get, uri = "/happening/$newSlug/registrations?download=y&testing=y"
                )

                getRegistrationsListCall.response.status() shouldBe HttpStatusCode.OK
                getRegistrationsListCall.response.content!! shouldBe toCsv(regsList, testing = true)

                val getRegistrationsListJsonCall = handleRequest(
                    method = HttpMethod.Get, uri = "/happening/$newSlug/registrations?json=y&testing=y"
                )

                getRegistrationsListJsonCall.response.status() shouldBe HttpStatusCode.OK
                val registrationsList = Json.decodeFromString<List<RegistrationJson>>(
                    getRegistrationsListJsonCall.response.content!!,
                )
                registrationsList.map {
                    it.copy(submitDate = null)
                } shouldBe regsList
            }
        }
    }

    "Should respond properly when given invalid slug of happening when happening info is requested" {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            for (t in be) {
                val getHappeningInfoCall: TestApplicationCall = handleRequest(
                    method = HttpMethod.Get, uri = "/happening//registrations"
                ) {
                    addHeader(
                        HttpHeaders.Authorization, basicAuth
                    )
                }

                getHappeningInfoCall.response.status() shouldBe HttpStatusCode.NotFound
                getHappeningInfoCall.response.content!! shouldBe "Happening doesn't exist."
            }
        }
    }

    "Should accept registrations for happening with infinite spots" {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            fun submitReg(type: HAPPENING_TYPE, i: Int) {
                val submitRegCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap7(type).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(
                            Json.encodeToString(
                                exReg(type).copy(
                                    email = "${type}test$i@test.com"
                                )
                            )
                        )
                    }

                submitRegCall.response.status() shouldBe HttpStatusCode.OK
                val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                res shouldNotBe null
                res.code shouldBe RegistrationResponse.OK
            }

            for (t in be) {
                for (i in 1..1000) {
                    submitReg(t, i)
                }
            }
        }
    }

    "Should delete registrations properly" {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles)
        }) {
            val waitListAmount = 3

            for (t in be) {
                for (i in 1..hap9(t).spotRanges[0].spots) {
                    val submitRegCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap9(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(
                                Json.encodeToString(
                                    exReg(t).copy(
                                        email = "${t}$i@test.com"
                                    )
                                )
                            )
                        }

                    submitRegCall.response.status() shouldBe HttpStatusCode.OK
                    val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.OK
                }

                for (i in 1..waitListAmount) {
                    val submitRegCall: TestApplicationCall =
                        handleRequest(method = HttpMethod.Post, uri = "/happening/${hap9(t).slug}/registrations") {
                            addHeader(HttpHeaders.ContentType, "application/json")
                            setBody(
                                Json.encodeToString(
                                    exReg(t).copy(
                                        email = "waitlist${t}$i@test.com"
                                    )
                                )
                            )
                        }

                    submitRegCall.response.status() shouldBe HttpStatusCode.Accepted
                    val res = Json.decodeFromString<RegistrationResponseJson>(submitRegCall.response.content!!)

                    res shouldNotBe null
                    res.code shouldBe RegistrationResponse.WaitList
                }

                // Delete $waitListAmount registrations, such that all the registrations
                // previously on the wait list are now moved off the wait list.
                for (i in 1..waitListAmount) {
                    val regEmail = "${t}$i@test.com"
                    val nextRegOnWaitListEmail = "waitlist${t}$i@test.com"
                    val deleteRegCall: TestApplicationCall = handleRequest(
                        method = HttpMethod.Delete, uri = "/happening/${hap9(t).slug}/registrations/$regEmail"
                    )

                    deleteRegCall.response.status() shouldBe HttpStatusCode.OK
                    deleteRegCall.response.content shouldBe "Registration with email = ${regEmail.lowercase()} and slug = ${
                    hap9(t).slug
                    } deleted, " + "and registration with email = ${nextRegOnWaitListEmail.lowercase()} moved off wait list."
                }

                // Delete the registrations that were moved off the wait list in the previous for-loop.
                for (i in 1..waitListAmount) {
                    val waitListRegEmail = "waitlist${t}$i@test.com"
                    val deleteWaitListRegCall: TestApplicationCall = handleRequest(
                        method = HttpMethod.Delete, uri = "/happening/${hap9(t).slug}/registrations/$waitListRegEmail"
                    )

                    deleteWaitListRegCall.response.status() shouldBe HttpStatusCode.OK
                    deleteWaitListRegCall.response.content!! shouldBe "Registration with email = ${waitListRegEmail.lowercase()} and slug = ${
                    hap9(
                        t
                    ).slug
                    } deleted."
                }
            }
        }
    }

    "Should only be able to sign in via form" {
        withTestApplication({
            configureRouting(adminKey, null, true, featureToggles.copy(verifyRegs = true))
        }) {
            for (t in be) {
                val submitRegFailCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t)))
                    }

                submitRegFailCall.response.status() shouldBe HttpStatusCode.Unauthorized
                val resFail = Json.decodeFromString<RegistrationResponseJson>(submitRegFailCall.response.content!!)

                resFail shouldNotBe null
                resFail.code shouldBe RegistrationResponse.NotViaForm

                val submitRegOkCall: TestApplicationCall =
                    handleRequest(method = HttpMethod.Post, uri = "/happening/${hap1(t).slug}/registrations") {
                        addHeader(HttpHeaders.ContentType, "application/json")
                        setBody(Json.encodeToString(exReg(t).copy(regVerifyToken = hap1(t).slug)))
                    }

                submitRegOkCall.response.status() shouldBe HttpStatusCode.OK
                val resOk = Json.decodeFromString<RegistrationResponseJson>(submitRegOkCall.response.content!!)

                resOk shouldNotBe null
                resOk.code shouldBe RegistrationResponse.OK
            }
        }
    }
})
