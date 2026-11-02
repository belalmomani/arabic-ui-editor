package com.arabicuieditor.ui.editor.canvas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** يحوّل معرّف الأيقونة (انظر IconCatalog) إلى الرسم الفعلي. */
fun iconVector(id: String): ImageVector? = when (id) {
    "Home" -> Icons.Filled.Home
    "Search" -> Icons.Filled.Search
    "Settings" -> Icons.Filled.Settings
    "Person" -> Icons.Filled.Person
    "AccountCircle" -> Icons.Filled.AccountCircle
    "Favorite" -> Icons.Filled.Favorite
    "FavoriteBorder" -> Icons.Filled.FavoriteBorder
    "Star" -> Icons.Filled.Star
    "Menu" -> Icons.Filled.Menu
    "MoreVert" -> Icons.Filled.MoreVert
    "Add" -> Icons.Filled.Add
    "Close" -> Icons.Filled.Close
    "Clear" -> Icons.Filled.Clear
    "Check" -> Icons.Filled.Check
    "CheckCircle" -> Icons.Filled.CheckCircle
    "Done" -> Icons.Filled.Done
    "Delete" -> Icons.Filled.Delete
    "Edit" -> Icons.Filled.Edit
    "Create" -> Icons.Filled.Create
    "Share" -> Icons.Filled.Share
    "Info" -> Icons.Filled.Info
    "Warning" -> Icons.Filled.Warning
    "Email" -> Icons.Filled.Email
    "Phone" -> Icons.Filled.Phone
    "Call" -> Icons.Filled.Call
    "Lock" -> Icons.Filled.Lock
    "Notifications" -> Icons.Filled.Notifications
    "ShoppingCart" -> Icons.Filled.ShoppingCart
    "Refresh" -> Icons.Filled.Refresh
    "PlayArrow" -> Icons.Filled.PlayArrow
    "ThumbUp" -> Icons.Filled.ThumbUp
    "Face" -> Icons.Filled.Face
    "Build" -> Icons.Filled.Build
    "DateRange" -> Icons.Filled.DateRange
    "LocationOn" -> Icons.Filled.LocationOn
    "Place" -> Icons.Filled.Place
    "KeyboardArrowDown" -> Icons.Filled.KeyboardArrowDown
    "KeyboardArrowUp" -> Icons.Filled.KeyboardArrowUp
    "KeyboardArrowLeft" -> Icons.Filled.KeyboardArrowLeft
    "KeyboardArrowRight" -> Icons.Filled.KeyboardArrowRight
    else -> null
}
