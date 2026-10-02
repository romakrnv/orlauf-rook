package com.orlauf.rook

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserRulesTest {

    private val anna = User("a", "Аня", "🦊", Avatars.COLORS[0], Profile())
    private val boris = User("b", "Боря", "🐻", Avatars.COLORS[1], Profile())

    @Test
    fun `favorite is selected on start`() {
        assertThat(UserRules.initial(listOf(anna, boris), favoriteId = "b")).isEqualTo(boris)
    }

    @Test
    fun `first user is selected when there is no favorite`() {
        assertThat(UserRules.initial(listOf(anna, boris), favoriteId = null)).isEqualTo(anna)
    }

    @Test
    fun `first user is selected when favorite was deleted`() {
        assertThat(UserRules.initial(listOf(anna, boris), favoriteId = "gone")).isEqualTo(anna)
    }

    @Test
    fun `no users gives no selection`() {
        assertThat(UserRules.initial(emptyList(), favoriteId = "a")).isNull()
    }

    @Test
    fun `new user gets avatar nobody has yet`() {
        val taken = listOf(
            anna.copy(emoji = Avatars.EMOJI[0], color = Avatars.COLORS[0]),
            boris.copy(emoji = Avatars.EMOJI[1], color = Avatars.COLORS[1]),
        )

        val user = UserRules.newUser(taken)

        assertThat(user.emoji).isEqualTo(Avatars.EMOJI[2])
        assertThat(user.color).isEqualTo(Avatars.COLORS[2])
        assertThat(user.name).isEqualTo("Пользователь 3")
        assertThat(user.id).isNotIn("a", "b")
    }

    @Test
    fun `avatars repeat when all are taken`() {
        val everyone = Avatars.EMOJI.mapIndexed { i, e ->
            User("$i", "$i", e, Avatars.COLORS[i % Avatars.COLORS.size], Profile())
        }

        assertThat(UserRules.nextAvatar(everyone)).isEqualTo(Avatars.EMOJI.first() to Avatars.COLORS.first())
    }
}
