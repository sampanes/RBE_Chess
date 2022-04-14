package com.example.rbechess

enum class fromToRankFile(val value: Int) {
    FF(0), FR(1), TF(2), TR(3)
}

class NextMoveObj {
    // property
    /*
    private var fromFile: Char = 'A'
    private var fromRank: Char = '1'
    private var toFile: Char = 'A'
    private var toRank: Char = '1'
    */
    private var charsFromToRankFile : CharArray = charArrayOf('A', '1', 'A', '1')

    fun getNMOstr(): String {
        return charsFromToRankFile.joinToString("")
    }

    fun iterVar(input: fromToRankFile) : Char {
        when(input) {

            fromToRankFile.FF, fromToRankFile.TF -> {
                if (charsFromToRankFile[input.value] < 'H') { ++charsFromToRankFile[input.value] }
                else { charsFromToRankFile[input.value] = 'A' }
            }

            fromToRankFile.FR, fromToRankFile.TR -> {
                if (charsFromToRankFile[input.value] < '8') { ++charsFromToRankFile[input.value] }
                else { charsFromToRankFile[input.value] = '1' }
            }

        }
        return charsFromToRankFile[input.value]
    }
}
