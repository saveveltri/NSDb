import com.typesafe.sbt.site.SitePlugin.autoImport._
import microsites.MicrositeKeys._
import sbt.Keys._
import sbt._
//import sbtorgpolicies.OrgPoliciesPlugin
//import sbtorgpolicies.OrgPoliciesPlugin.autoImport._
//import sbtorgpolicies.runnable.syntax._

object Microsite {
  lazy val settings: Seq[Def.Setting[_]] = Seq(
    micrositeName := "NSDb",
    micrositeDescription := "NSDb",
//    micrositeBaseUrl := "nsdb",
    micrositeDocumentationUrl := "docs",
    micrositeGithubOwner := "radicalbit",
    micrositeGithubRepo := "NSDb",
    micrositeFooterText := None,
//    micrositeGithubToken := sys.env.get(orgGithubTokenSetting.value),
//    micrositePushSiteWith := GitHub4s,
    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.md" | "*.svg"
  )
}