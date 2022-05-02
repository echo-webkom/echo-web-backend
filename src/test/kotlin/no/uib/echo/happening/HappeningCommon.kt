package no.uib.echo.happening

import no.uib.echo.schema.HAPPENING_TYPE
import no.uib.echo.schema.HappeningWithSlugJson
import no.uib.echo.schema.SpotRangeJson

val be = listOf(HAPPENING_TYPE.BEDPRES, HAPPENING_TYPE.EVENT)
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
