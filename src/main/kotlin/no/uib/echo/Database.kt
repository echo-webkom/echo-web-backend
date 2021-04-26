package no.uib.echo

import org.jetbrains.exposed.sql.*
import com.zaxxer.hikari.*
import javax.sql.DataSource

object DatabaseConn {
    fun pool(): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:postgresql://[::]/postgres"
        config.username = "postgres"
        config.password = "password"
        config.driverClassName = "org.postgresql.Driver"

        return HikariDataSource(config)
    }
}

class FullRegistrationJson(
    val email: String,
    val firstName: String,
    val lastName: String,
    val degree: Degree,
    val slug: String,
    val terms: Boolean
)

data class BedpresJson(val slug: String, val spots: Int)

object Registration : Table() {
    val studentEmail = varchar("studentEmail", 40) references Student.email
    val bedpresSlug = varchar("bedpresSlug", 40) references Bedpres.slug
    val terms = bool("terms")

    override val primaryKey = PrimaryKey(studentEmail, bedpresSlug)
}

object Student : Table() {
    val email = varchar("email", 40).uniqueIndex()
    val firstName = varchar("firstName", 40)
    val lastName = varchar("lastName", 40)
    val degree = varchar("degree", 66)

    override val primaryKey = PrimaryKey(email)
}

object Bedpres : Table() {
    val slug = varchar("slug", 40).uniqueIndex()
    var spots = integer("spots")

    override val primaryKey = PrimaryKey(slug)
}

enum class Degree {
    DTEK,
    DSIK,
    DVIT
}