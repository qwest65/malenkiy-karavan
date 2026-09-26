package ru.cultureguide.kids.content

/** Вопрос для ребёнка: [answer] — номер правильного варианта в [options]. */
data class Quiz(val text: String, val options: List<String>, val answer: Int) {
    init {
        require(answer in options.indices) { "answer=$answer вне вариантов $options" }
    }

    fun isRight(option: Int): Boolean = option == answer
}
