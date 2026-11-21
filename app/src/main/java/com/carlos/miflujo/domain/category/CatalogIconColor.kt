package com.carlos.miflujo.domain.category

/** Stable presentation keys used by the experimental storage and wire formats. */
enum class CatalogIconColor(val key: String) {
    RED("red"), ORANGE("orange"), AMBER("amber"), GREEN("green"), MINT("mint"),
    CYAN("cyan"), BLUE("blue"), VIOLET("violet"), PINK("pink"), GRAY("gray");

    companion object {
        fun fromKey(key: String?): CatalogIconColor? = key?.let {
            requireNotNull(entries.firstOrNull { color -> color.key == it }) { "Unknown catalog icon color" }
        }
    }
}
