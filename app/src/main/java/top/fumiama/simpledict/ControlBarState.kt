package top.fumiama.simpledict

class ControlBarState(private var pageSize: Int) {
    var index = 0
        set(value) {
            if(total == 0) return
            if(value < 0 || value > total) return
            var s = value
            if(value+pageSize > total) s = total - pageSize
            if(s < 0) s = 0
            end = s + pageSize
            field = s
        }
    var total: Int = 0
        set(value) {
            if(value >= 0) field = value
        }
    var sort = SORT_EDIT_TIME_DOWN
        set(value) {
            if(value < 0 || value > SORT_LENGTH_DOWN) return
            field = value
        }
    private var end = pageSize

    fun formatRange(fmt: String) = fmt.format(index, end)

    fun formatSize(fmt: String) = fmt.format(total)
    fun getPosition(p: Int): Int {
        if(p > 100 || p < 0) return 0
        var newIndex = p * total / 100
        if(newIndex + pageSize > total) {
            newIndex = total - pageSize
            if(newIndex < 0) newIndex = 0
        }
        return newIndex
    }
    fun getPercentage() = if(total == 0) 0 else 100 * (index+end)/2 / total

    fun sort(keys: List<String>): List<String> {
        return when(sort) {
            SORT_EDIT_TIME_UP -> keys
            SORT_EDIT_TIME_DOWN -> keys.reversed()
            SORT_ALPHABET_UP -> keys.sorted()
            SORT_ALPHABET_DOWN -> keys.sorted().reversed()
            SORT_LENGTH_UP -> keys.sortedBy { k -> return@sortedBy k.length }
            SORT_LENGTH_DOWN -> keys.sortedBy { k -> return@sortedBy k.length }.reversed()
            else -> keys
        }
    }

    companion object {
        const val SORT_EDIT_TIME_UP = 0
        const val SORT_EDIT_TIME_DOWN = 1
        const val SORT_ALPHABET_UP = 2
        const val SORT_ALPHABET_DOWN = 3
        const val SORT_LENGTH_UP = 4
        const val SORT_LENGTH_DOWN = 5
    }
}
