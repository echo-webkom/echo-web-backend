package no.uib.echo.happening

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.uib.echo.DatabaseHandler
import no.uib.echo.plugins.Routing
import no.uib.echo.schema.Happening
import no.uib.echo.schema.SpotRange
import no.uib.echo.schema.insertOrUpdateHappening
import no.uib.echo.schema.removeSlug
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

class DeleteHappeningTest : StringSpec({
    val client = HttpClient {
        install(Logging)
        install(ContentNegotiation)
    }

    beforeSpec {
        DatabaseHandler(true, URI(System.getenv("DATABASE_URL")), null).init()
        for (t in be) {
            withContext(Dispatchers.IO) {
                insertOrUpdateHappening(
                    removeSlug(exHap(t)), exHap(t).slug, null, sendEmail = false, dev = true
                )
            }
        }
    }
    afterSpec {
        client.close()
    }

    beforeTest {
        transaction {
            SchemaUtils.drop(
                Happening, SpotRange
            )
            SchemaUtils.create(
                Happening, SpotRange
            )
        }
    }

    "When trying to delete a happening, server should respond with OK." {
        for (t in be) {
            val testCall = client.delete(routeWithSlug(exHap(t).slug)) {
                basicAuth("admin", System.getenv("ADMIN_KEY"))
            }

            testCall.status shouldBe HttpStatusCode.OK
        }
    }

    "When trying to delete a happening with wrong Authorization header, server should respond with UNAUTHORIZED." {
        for (t in be) {
            val testCall = client.delete(routeWithSlug(exHap(t).slug)) {
                basicAuth("admin", "wrong-password")
            }

            testCall.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})

private fun routeWithSlug(slug: String): String {
    return Routing.deleteHappeningRoute.replace("{slug}", slug)
}
