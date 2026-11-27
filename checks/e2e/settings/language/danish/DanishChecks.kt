package com.ffmathy.pooparazzi.checks

import java.util.Locale
import org.junit.Test
import org.robolectric.annotation.Config

/** End-to-end check for Danish: the setup and Settings on a phone set to Danish, on the real phone app. */
@Config(qualifiers = "da-w390dp-h844dp-xxhdpi")
class DanishChecks : Phone() {
    private val danish = Locale.forLanguageTag("da")

    private fun onDitBarn() {
        waitForInDialog("Spring over for nu")
        pass(500)
    }

    /** The first run skipped in Danish, then Indstillinger. */
    private fun openIndstillinger() {
        onDitBarn()
        tap("Spring over for nu")
        waitForInDialog("Spring over")
        pass(500)
        tap("Spring over")
        waitForInDialog("Start søvn")
        pass(1_000)
        tap("Indstillinger")
        waitForInDialog("Udseende")
        pass(500)
    }

    @Test
    fun `your baby on a danish phone`() {
        onDitBarn()
        assertShows("Dit barn")
        assertShows("Begge dele er valgfrie. Du kan springe dem over nu og tilføje dem senere under Indstillinger › Barn.")
        assertShows("Navn")
        assertCanTap("Tilføj fødselsdato")
        assertCanTap("Fortsæt")
        assertCanTap("Spring over for nu")
        tapInDialog("Tilføj fødselsdato")
        waitForInDialog("Fødselsdato")
        pickTodayAsBirthDate()
        waitForInDialog(bornToday(danish))
    }

    @Test
    fun `the introduction on a danish phone`() {
        onDitBarn()
        tap("Spring over for nu")
        waitForInDialog("Velkommen til Pooparazzi")
        pass(500)
        val titles = listOf("Velkommen til Pooparazzi", "Søvn", "Måltider og bleskift", "Ret hvad som helst", "Hvad kommer nu", "Altid ved hånden")
        titles.forEachIndexed { index, title ->
            if (index > 0) {
                tap("Næste")
                waitForInDialog(title)
                pass(500)
            }
            assertShows("Side ${index + 1} af 6")
        }
        assertCanTap("Kom i gang")
        assertGone("Spring over")
        assertGone("Næste")
    }

    @Test
    fun `settings on a danish phone`() {
        openIndstillinger()
        assertShowsInOrder("Konto", "Barn", "Udseende", "Sprog", "Ur", "Notifikationer", "Om")
        assertShows("Backup er ikke sat op i denne version. Alt gemmes på denne telefon.")
        assertShowsInOrder("Lavet med ❤️ af en far i Aarhus", "Pooparazzi er open source")
    }

    @Test
    fun `adding and removing a baby in danish`() {
        openIndstillinger()
        tapInDialog("Tilføj et barn")
        waitForInDialog("Annuller")
        typeName("Ida")
        tapInDialog("Tilføj")
        waitUntilGoneInDialog("Annuller")
        waitForInDialog("Børn")
        assertShows("Fjern Baby 1")
        assertShows("Fjern Ida")
        tapInDialog("Fjern Ida")
        waitForInDialog("Fjern Ida?")
        assertShows("Barnets log bliver slettet fra telefonen og dit ur. Det kan ikke fortrydes.")
        assertCanTap("Annuller")
        assertCanTap("Fjern")
    }
}
