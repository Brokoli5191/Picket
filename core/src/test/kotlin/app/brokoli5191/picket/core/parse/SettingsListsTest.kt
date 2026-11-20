package app.brokoli5191.picket.core.parse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsListsTest {
    private val talkback =
        Component("com.google.android.marvin.talkback", "com.google.android.marvin.talkback.TalkBackService")

    @Test
    fun `component parsing handles both flattened forms and rejects junk`() {
        assertThat(Component.parse("com.example/.Svc")).isEqualTo(Component("com.example", "com.example.Svc"))
        assertThat(Component.parse(" com.example/com.other.Svc\n")).isEqualTo(Component("com.example", "com.other.Svc"))
        assertThat(Component.parse("com.example")).isNull()
        assertThat(Component.parse("com.example/.Svc;rm")).isNull()
        assertThat(Component("com.example", "com.example.Svc").short).isEqualTo("com.example/.Svc")
        assertThat(Component("com.example", "com.other.Svc").short).isEqualTo("com.example/com.other.Svc")
        assertThat(Component("com.example", "com.example.Svc").full).isEqualTo("com.example/com.example.Svc")
    }

    @Test
    fun `component lists`() {
        val value = "com.google.android.marvin.talkback/.TalkBackService:com.example.pass/com.example.pass.Autofill\n"
        assertThat(SettingsLists.components(value).map { it.packageName })
            .containsExactly("com.google.android.marvin.talkback", "com.example.pass")
            .inOrder()
        assertThat(SettingsLists.components("null")).isEmpty()
        assertThat(SettingsLists.components("")).isEmpty()
        assertThat(SettingsLists.components("garbage:com.a/.B")).containsExactly(Component("com.a", "com.a.B"))
    }

    @Test
    fun `package lists and flags`() {
        assertThat(SettingsLists.packages("com.a:com.b\n")).containsExactly("com.a", "com.b").inOrder()
        assertThat(SettingsLists.packages("null")).isEmpty()
        assertThat(SettingsLists.packages("com.a:bad pkg")).containsExactly("com.a")
        assertThat(SettingsLists.flag("1\n")).isTrue()
        assertThat(SettingsLists.flag("0")).isFalse()
        assertThat(SettingsLists.flag("null")).isFalse()
    }

    @Test
    fun `input methods drop subtype hashes`() {
        val value =
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME;-921088104;529847764:" +
                "com.samsung.android.honeyboard/.service.HoneyBoardService"
        assertThat(SettingsLists.inputMethods(value).map { it.short })
            .containsExactly(
                "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
                "com.samsung.android.honeyboard/.service.HoneyBoardService",
            ).inOrder()
        assertThat(SettingsLists.inputMethods("null")).isEmpty()
        assertThat(
            SettingsLists.singleComponent("com.samsung.android.honeyboard/.service.HoneyBoardService")?.packageName,
        ).isEqualTo("com.samsung.android.honeyboard")
        assertThat(SettingsLists.singleComponent("null")).isNull()
    }

    @Test
    fun `removing one service keeps every other entry byte for byte`() {
        val value =
            "com.a/.One:" +
                "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService:" +
                "com.b/com.b.Two"
        assertThat(SettingsLists.withoutComponent(value, talkback)).isEqualTo("com.a/.One:com.b/com.b.Two")
        assertThat(
            SettingsLists.withoutComponent("com.google.android.marvin.talkback/.TalkBackService", talkback),
        ).isEmpty()
        assertThat(SettingsLists.withoutComponent("null", talkback)).isEmpty()
        assertThat(SettingsLists.withoutComponent("com.a/.One", talkback)).isEqualTo("com.a/.One")
    }

    @Test
    fun `adding a service is idempotent`() {
        assertThat(
            SettingsLists.withComponent("null", talkback),
        ).isEqualTo("com.google.android.marvin.talkback/.TalkBackService")
        assertThat(SettingsLists.withComponent("com.a/.One", talkback))
            .isEqualTo("com.a/.One:com.google.android.marvin.talkback/.TalkBackService")
        assertThat(
            SettingsLists.withComponent("com.a/.One:com.google.android.marvin.talkback/.TalkBackService", talkback),
        ).isEqualTo("com.a/.One:com.google.android.marvin.talkback/.TalkBackService")
    }
}
