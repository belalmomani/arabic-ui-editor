package com.arabicuieditor.data.model

/** أيقونات Material الأساسية المتاحة لعنصر «صورة». المعرّف = اسم الأيقونة في Icons.Filled. */
object IconCatalog {
    val ids: List<String> = listOf(
        "Home", "Search", "Settings", "Person", "AccountCircle", "Favorite", "FavoriteBorder", "Star",
        "Menu", "MoreVert", "Add", "Close", "Clear", "Check", "CheckCircle", "Done",
        "Delete", "Edit", "Create", "Share", "Info", "Warning", "Email", "Phone",
        "Call", "Lock", "Notifications", "ShoppingCart", "Refresh", "PlayArrow", "ThumbUp", "Face",
        "Build", "DateRange", "LocationOn", "Place",
        "KeyboardArrowDown", "KeyboardArrowUp", "KeyboardArrowLeft", "KeyboardArrowRight"
    )
    private val set = ids.toSet()
    fun isValid(id: String): Boolean = id in set
}
