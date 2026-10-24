package com.browserz.engine.adblock

import com.browserz.core.domain.RequestType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private val builtin = FilterEngine.parse(File("src/main/assets/builtin.txt").readText())

    private fun blocked(url: String, source: String? = "https://news.example.com/", type: RequestType = RequestType.Script) =
        builtin.shouldBlock(url, source?.let { FilterEngine.hostOf(it) }, type)

    @Test
    fun `given an adsense script when checking then it is blocked`() {
        assertTrue(blocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-1"))
        assertTrue(blocked("https://tpc.googlesyndication.com/simgad/123", type = RequestType.Image))
        assertTrue(blocked("https://googleads.g.doubleclick.net/pagead/ads?client=x", type = RequestType.SubFrame))
    }

    @Test
    fun `given host rules when checking then subdomains match but lookalikes do not`() {
        assertTrue(blocked("https://a.b.doubleclick.net/x"))
        assertFalse(blocked("https://doubleclick.net.example.org/x"))
        assertFalse(blocked("https://notdoubleclick.net/x"))
    }

    @Test
    fun `given a normal page resource when checking then it is allowed`() {
        assertFalse(blocked("https://news.example.com/static/app.js"))
        assertFalse(blocked("https://cdn.example.com/images/logo.png", type = RequestType.Image))
    }

    @Test
    fun `given the main document when checking then it is never blocked`() {
        assertFalse(blocked("https://doubleclick.net/", source = null, type = RequestType.Document))
    }

    @Test
    fun `given a third-party rule when same site then it is allowed`() {
        assertTrue(blocked("https://www.google-analytics.com/analytics.js", source = "https://news.example.com/"))
        assertFalse(blocked("https://www.google-analytics.com/analytics.js", source = "https://www.google-analytics.com/"))
    }

    @Test
    fun `given an exception rule when it matches then the request is allowed`() {
        val engine = FilterEngine.parse("||ads.example.com^\n@@||ads.example.com/allowed/\n")
        assertTrue(engine.shouldBlock("https://ads.example.com/x.js", "site.test", RequestType.Script))
        assertFalse(engine.shouldBlock("https://ads.example.com/allowed/x.js", "site.test", RequestType.Script))
    }

    @Test
    fun `given type options when checking then only those types are blocked`() {
        val engine = FilterEngine.parse("||cdn.test/track\$image\n")
        assertTrue(engine.shouldBlock("https://cdn.test/track.gif", "a.test", RequestType.Image))
        assertFalse(engine.shouldBlock("https://cdn.test/track.js", "a.test", RequestType.Script))
    }

    @Test
    fun `given a domain option when checking then only those source sites are affected`() {
        val engine = FilterEngine.parse("/promo.js\$domain=shop.test|~pay.shop.test\n")
        assertTrue(engine.shouldBlock("https://x.test/promo.js", "shop.test", RequestType.Script))
        assertFalse(engine.shouldBlock("https://x.test/promo.js", "pay.shop.test", RequestType.Script))
        assertFalse(engine.shouldBlock("https://x.test/promo.js", "other.test", RequestType.Script))
    }

    @Test
    fun `given wildcard and separator patterns when checking then they match like abp`() {
        val engine = FilterEngine.parse("/banner/*/ads^\n|https://exact.test/a|\n")
        assertTrue(engine.shouldBlock("https://s.test/banner/728/ads?x=1", "a.test", RequestType.Image))
        assertFalse(engine.shouldBlock("https://s.test/banner/728/adsx", "a.test", RequestType.Image))
        assertTrue(engine.shouldBlock("https://exact.test/a", "a.test", RequestType.Image))
        assertFalse(engine.shouldBlock("https://exact.test/a/b", "a.test", RequestType.Image))
    }

    @Test
    fun `given unsupported options when parsing then the rule is skipped`() {
        val engine = FilterEngine.parse("||x.test^\$redirect=noop.js\n||y.test^\$removeparam=x\n")
        assertEquals(0, engine.networkRuleCount)
    }

    @Test
    fun `given cosmetic rules when asking for css then generic and domain rules are returned`() {
        val engine = FilterEngine.parse("##.adsbygoogle\nshop.test##.promo-box\n##div:-abp-has(.x)\n")
        assertTrue(engine.cosmeticCss("other.test").contains(".adsbygoogle{display:none!important}"))
        assertFalse(engine.cosmeticCss("other.test").contains("promo-box"))
        assertTrue(engine.cosmeticCss("www.shop.test").contains(".promo-box{display:none!important}"))
        assertFalse(engine.cosmeticCss("shop.test").contains("abp"))
    }

    @Test
    fun `given the builtin list when asking for css then adsense slots are hidden`() {
        val css = builtin.cosmeticCss("example.org")
        assertTrue(css.contains("ins.adsbygoogle"))
        assertTrue(css.contains("div-gpt-ad"))
    }

    @Test
    fun `given a malformed list when parsing then it never throws`() {
        val junk = listOf("", "!", "[", "||", "|", "*", "\$", "##", "a##", "@@", "/(/", "||a^\$domain=", "x\$unknown")
        FilterEngine.parse(junk.asSequence())
    }

    @Test
    fun `given a popup rule when checking then only navigations are blocked`() {
        val engine = FilterEngine.parse("||ad-popups.test^\$popup\n")
        assertTrue(engine.shouldBlock("https://ad-popups.test/go", "site.test", RequestType.Popup))
        assertFalse(engine.shouldBlock("https://ad-popups.test/x.js", "site.test", RequestType.Script))
    }

    @Test
    fun `given a bare host rule when checking a navigation then it is blocked like a request`() {
        val engine = FilterEngine.parse("||shameful-farm.test^\n")
        assertTrue(engine.shouldBlock("https://shameful-farm.test/b/abc", "dramatip.test", RequestType.Popup))
        assertTrue(engine.shouldBlock("https://shameful-farm.test/b/abc", "dramatip.test", RequestType.Script))
        assertFalse(engine.shouldBlock("https://dramatip.test/play", "dramatip.test", RequestType.Popup))
    }

    @Test
    fun `given a rule limited to scripts when checking a navigation then it does not match`() {
        val engine = FilterEngine.parse("||cdn.test/lib^\$script\n")
        assertFalse(engine.shouldBlock("https://cdn.test/lib", "a.test", RequestType.Popup))
    }

    @Test
    fun `given a domain list in hagezi style when checking then every host is covered`() {
        val engine = FilterEngine.parse("[Adblock Plus]\n! Title: test\n||aarems.test^\n||5gvci.test^\n")
        assertEquals(2, engine.networkRuleCount)
        assertTrue(engine.shouldBlock("https://sub.aarems.test/1/hash", "x.test", RequestType.Script))
        assertTrue(engine.shouldBlock("https://5gvci.test/act/files/tag.min.js", "x.test", RequestType.Script))
    }

    @Test
    fun `given plain host lines and lookalike lines when parsing then only exact host rules take the fast path`() {
        val engine = FilterEngine.parse("||fast.test^\n||Mixed-Case.Test^\n||path.test/x^\n||wild*.test^\n||nodot^\n@@||fast.test^\n")
        assertTrue(engine.shouldBlock("https://sub.mixed-case.test/a.js", "x.test", RequestType.Script))
        assertFalse(engine.shouldBlock("https://fast.test/a.js", "x.test", RequestType.Script)) // the exception wins
        assertTrue(engine.shouldBlock("https://path.test/x?y", "x.test", RequestType.Script))
        assertTrue(engine.shouldBlock("https://nodot/", "x.test", RequestType.Script)) // single-label hosts skip the fast path but still work
    }

    @Test
    fun `given element-hide exceptions when asking for css then excepted selectors stay visible on that site only`() {
        val engine = FilterEngine.parse("##.ad-box\n##.banner\nnews.test#@#.ad-box\n#@#.banner\nshop.test##.promo\nwww.shop.test#@#.promo\n")
        assertFalse(engine.cosmeticCss("news.test").contains(".ad-box"))
        assertTrue(engine.cosmeticCss("other.test").contains(".ad-box"))
        assertFalse(engine.cosmeticCss("other.test").contains(".banner")) // generic exception removes it everywhere
        assertTrue(engine.cosmeticCss("shop.test").contains(".promo"))
        assertFalse(engine.cosmeticCss("www.shop.test").contains(".promo"))
        assertTrue("news.test" in engine.genericSkipHosts())
    }

    @Test
    fun `given generichide and elemhide switches when asking for css then hiding is switched off for those sites`() {
        val engine = FilterEngine.parse("##.ad-box\napp.test##.promo\nshop.test##.promo\n@@||app.test^\$generichide\n@@||shop.test^\$elemhide\n")
        assertEquals(".promo{display:none!important}", engine.cosmeticCss("app.test"))
        assertEquals("", engine.cosmeticCss("m.shop.test"))
        assertTrue(engine.genericSkipHosts().containsAll(setOf("app.test", "shop.test")))
        assertFalse(engine.shouldBlock("https://app.test/x.js", "a.test", RequestType.Script)) // not read as a network rule
    }

    @Test
    fun `given the generic css was already injected when asking for site css then only the site rules are returned`() {
        val engine = FilterEngine.parse("##.ad-box\nshop.test##.promo\n")
        assertEquals(".promo{display:none!important}", engine.siteCss("shop.test", genericApplied = true))
        assertEquals("", engine.siteCss("other.test", genericApplied = true))
    }

    @Test
    fun `given procedural rules when compiling then supported ones become programs and the rest are skipped`() {
        val engine = FilterEngine.parse(
            "feed.test##div.post:has-text(Sponsored):upward(2)\n" +
                "feed.test#?#article:-abp-contains(/promo(ted)?/i) > .media\n" +
                "feed.test##.card:-abp-has(a[href*=\"ads\"]):remove()\n" +
                "feed.test##div:xpath(//div)\n" +
                "feed.test##div:has-text(x) + span\n",
        )
        val rules = engine.proceduralRules("www.feed.test")
        assertEquals(
            "[[\"div.post\",[\"text\",\"Sponsored\"],[\"up\",\"2\"]]," +
                "[\"article\",[\"text\",\"/promo(ted)?/i\"],[\"down\",\":scope > .media\"]]," +
                "[\".card\",[\"has\",\"a[href*=\\\"ads\\\"]\"],[\"remove\"]]]",
            rules,
        )
        assertEquals("", engine.proceduralRules("other.test"))
        assertFalse(engine.cosmeticCss("feed.test").contains("has-text"))
    }

    @Test
    fun `given a procedural rule with an exception when asking then it is not run`() {
        val engine = FilterEngine.parse("feed.test##div:has-text(Ad)\nm.feed.test#@#div:has-text(Ad)\n")
        assertTrue(engine.proceduralRules("feed.test").isNotEmpty())
        assertEquals("", engine.proceduralRules("m.feed.test"))
    }

    @Test
    fun `given text arguments with brackets and quotes when compiling then they are kept as text`() {
        val engine = FilterEngine.parse("q.test##span:has-text(Don't [miss]):upward(div.box)\n")
        assertEquals("[[\"span\",[\"text\",\"Don't [miss]\"],[\"up\",\"div.box\"]]]", engine.proceduralRules("q.test"))
    }

    @Test
    fun `given a saved engine when loading it then it blocks and hides exactly like the original`() {
        val original = FilterEngine.parse(
            File("src/main/assets/builtin.txt").readText() +
                "\n||track.test/pixel\$image,third-party,domain=a.test|~b.a.test\n@@||ads.test/ok/\n||ads.test^\n" +
                "news.test#@#.ad-box\n@@||app.test^\$generichide\nfeed.test##div:has-text(Ad)\n",
        )
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { original.writeTo(it, 42L) }
        val loaded = java.io.DataInputStream(bytes.toByteArray().inputStream()).use { FilterEngine.readFrom(it, 42L) }!!

        assertEquals(original.networkRuleCount, loaded.networkRuleCount)
        assertEquals(original.cosmeticRuleCount, loaded.cosmeticRuleCount)
        for (host in listOf("example.org", "news.test", "app.test", "feed.test")) {
            assertEquals(original.cosmeticCss(host), loaded.cosmeticCss(host))
            assertEquals(original.proceduralRules(host), loaded.proceduralRules(host))
        }
        assertEquals(original.genericSkipHosts(), loaded.genericSkipHosts())
        val probes = listOf(
            Triple("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", "news.example.com", RequestType.Script),
            Triple("https://track.test/pixel.gif", "a.test", RequestType.Image),
            Triple("https://track.test/pixel.gif", "b.a.test", RequestType.Image),
            Triple("https://ads.test/ok/x.js", "z.test", RequestType.Script),
            Triple("https://ads.test/no/x.js", "z.test", RequestType.Script),
            Triple("https://cdn.example.com/app.js", "example.com", RequestType.Script),
        )
        for ((url, source, type) in probes) assertEquals(url, original.shouldBlock(url, source, type), loaded.shouldBlock(url, source, type))
    }

    @Test
    fun `given a saved engine from other inputs when loading then it is rejected`() {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { FilterEngine.parse("||a.test^\n").writeTo(it, 1L) }
        assertEquals(null, java.io.DataInputStream(bytes.toByteArray().inputStream()).use { FilterEngine.readFrom(it, 2L) })
    }

    @Test
    fun `given a native has with a procedural argument when compiling then it becomes a nested program`() {
        val engine = FilterEngine.parse(
            "shop.test#?#.product-card:has(span:has-text(/^Ad\$/))\nshop.test##^script:has-text(ads)\nshop.test##.box:has(.x)\n",
        )
        assertEquals("[[\".product-card\",[\"hasp\",[\"span\",[\"text\",\"/^Ad\$/\"]]]]]", engine.proceduralRules("shop.test"))
        assertTrue(engine.cosmeticCss("shop.test").contains(".box:has(.x){display:none!important}")) // native :has stays CSS
    }
}
