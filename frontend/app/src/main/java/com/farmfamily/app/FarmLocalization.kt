package com.farmfamily.app

/** Common farmer-facing navigation and settings labels in Awadhi. */
internal fun farmLabel(english: String, awadhi: Boolean): String {
    if (!awadhi) return english
    return when (english) {
        "Home" -> "घर"
        "Records" -> "रिकॉर्ड"
        "Reports" -> "रिपोर्ट"
        "AI" -> "सहायता"
        "More" -> "अउर"
        "Settings" -> "सेटिंग"
        "Profile" -> "परिचय"
        "Family members" -> "परिवार के लोग"
        "Family settings" -> "परिवार सेटिंग"
        "Invitations" -> "नेवता"
        "Backup & restore" -> "बैकअप अउर बहाली"
        "Recycle bin" -> "हटावल रिकॉर्ड"
        "Sign out" -> "बाहर निकलीं"
        "Family workspace and account" -> "परिवार अउर खाता"
        "Search records" -> "रिकॉर्ड खोजीं"
        "Notifications" -> "सूचना"
        "Add a farm record" -> "खेती के रिकॉर्ड जोड़ीं"
        "Record details" -> "रिकॉर्ड के जानकारी"
        "Edit record" -> "रिकॉर्ड बदलीं"
        "Your name" -> "आपन नाम"
        "Email address" -> "ईमेल पता"
        "Dark theme" -> "रात के रंग"
        "Awadhi language (अवधी)" -> "अवधी भाषा"
        "Save account" -> "खाता सहेजीं"
        "Save family" -> "परिवार सहेजीं"
        "Restore" -> "वापस लाईं"
        "Create invitation" -> "नेवता बनाईं"
        "Choose profile picture" -> "आपन फोटो चुनीं"
        "Change profile picture" -> "फोटो बदलीं"
        "Remove picture" -> "फोटो हटाईं"
        "Name and workspace details" -> "नाम अउर परिवार के जानकारी"
        "Roles and access" -> "भूमिका अउर पहुँच"
        "Invite a family member" -> "परिवार के सदस्य के नेवता दीं"
        "Export or restore family data" -> "परिवार के जानकारी सुरक्षित करीं या बहाल करीं"
        "Sync, help, and about" -> "सिंक, मदद अउर जानकारी"
        else -> english
    }
}
