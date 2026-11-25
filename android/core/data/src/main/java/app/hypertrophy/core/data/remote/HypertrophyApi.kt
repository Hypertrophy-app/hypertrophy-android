package app.hypertrophy.core.data.remote

import app.hypertrophy.core.data.BuildConfig

object HypertrophyApi {
    /**
     * Host of the deployed Worker. Debug builds default to the workers.dev
     * host; set HYPERTROPHY_API_BASE_URL in local.properties (or pass
     * -PHYPERTROPHY_API_BASE_URL) to switch to a branded domain without a
     * code change.
     */
    val BASE_URL: String = BuildConfig.API_BASE_URL
}
