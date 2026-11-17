package com.farmfamily.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit

internal val LocalFarmHindi = staticCompositionLocalOf { false }

private val HindiLabels = mapOf(
    "Home" to "होम", "Records" to "रिकॉर्ड", "Reports" to "रिपोर्ट", "AI" to "कृषि सहायक", "More" to "और",
    "home" to "होम", "record" to "रिकॉर्ड", "field" to "खेत", "crop" to "फसल", "expense" to "खर्च",
    "harvest" to "कटाई", "sale" to "बिक्री", "season" to "मौसम", "seed" to "बीज", "labor" to "मजदूरी",
    "Family" to "परिवार", "Family farm" to "परिवार का खेत", "Fields" to "खेत", "Field" to "खेत",
    "Crop seasons" to "फसल के मौसम", "Crops" to "फसलें", "Seeds" to "बीज", "Fertilizer" to "खाद",
    "Labor" to "मजदूरी", "Machinery" to "कृषि मशीनरी", "Expenses" to "खर्च", "Harvests" to "फसल की कटाई",
    "Sales" to "बिक्री", "Historical prices" to "पुराने बाज़ार भाव", "Expense categories" to "खर्च की श्रेणियाँ",
    "Settings" to "सेटिंग", "Profile" to "प्रोफ़ाइल", "Family members" to "परिवार के सदस्य",
    "Weather" to "मौसम", "Sunny" to "धूप", "Clear night" to "साफ़ रात", "Cloudy" to "बादल",
    "Rainy" to "बारिश", "Snowy" to "बर्फ़", "Foggy" to "कोहरा", "Thunderstorm" to "आंधी-तूफ़ान",
    "Family settings" to "परिवार की सेटिंग", "Invitations" to "निमंत्रण", "Notifications" to "सूचनाएँ",
    "Family settings saved." to "परिवार की सेटिंग सहेजी गई।", "Saving family settings…" to "परिवार की सेटिंग सहेजी जा रही है…",
    "Default field area unit" to "खेत के रकबे की सामान्य इकाई", "Choose a supported area unit." to "रकबे की दी गई इकाइयों में से चुनें।",
    "Save family" to "परिवार की जानकारी सहेजें", "Year" to "साल", "Month" to "महीना",
    "January" to "जनवरी", "February" to "फ़रवरी", "March" to "मार्च", "April" to "अप्रैल",
    "May" to "मई", "June" to "जून", "July" to "जुलाई", "August" to "अगस्त",
    "September" to "सितंबर", "October" to "अक्टूबर", "November" to "नवंबर", "December" to "दिसंबर",
    "OWNER" to "मालिक", "Owner" to "मालिक", "EDITOR" to "संपादक", "Editor" to "संपादक",
    "VIEWER" to "केवल देखने वाला", "Viewer" to "केवल देखने वाला", "PENDING" to "बाकी",
    "ACCEPTED" to "स्वीकार किया", "REVOKED" to "रद्द किया", "EXPIRED" to "समय समाप्त",
    "Backup & restore" to "बैकअप और पुनर्स्थापना", "Recycle bin" to "रीसायकल बिन", "Search records" to "रिकॉर्ड खोजें",
    "Add a farm record" to "खेती का रिकॉर्ड जोड़ें", "Record details" to "रिकॉर्ड की जानकारी", "Edit record" to "रिकॉर्ड बदलें",
    "Add record" to "रिकॉर्ड जोड़ें", "Save record" to "रिकॉर्ड सहेजें", "Save" to "सहेजें", "Cancel" to "रद्द करें",
    "Delete" to "हटाएँ", "Edit" to "बदलें", "Restore" to "वापस लाएँ", "Back" to "वापस", "Try again" to "फिर कोशिश करें",
    "Choose" to "चुनें", "Choose date" to "तारीख चुनें", "Set date" to "तारीख तय करें", "From date" to "शुरू की तारीख",
    "To date" to "आखिरी तारीख", "Date" to "तारीख", "Start date" to "शुरू की तारीख", "End date" to "समाप्ति की तारीख",
    "Field name" to "खेत का नाम", "Area" to "रकबा", "Area unit" to "रकबे की इकाई", "Location" to "जगह",
    "Latitude" to "अक्षांश", "Longitude" to "देशांतर", "Notes" to "नोट", "Season name" to "मौसम का नाम",
    "Season area" to "मौसम का रकबा", "Crop" to "फसल", "Crop name" to "फसल का नाम", "Variety" to "किस्म",
    "Purpose" to "उद्देश्य", "All season costs recorded" to "मौसम के सभी खर्च दर्ज हैं", "All season revenue recorded" to "मौसम की सारी कमाई दर्ज है",
    "Seed name" to "बीज का नाम", "Product name" to "उत्पाद का नाम", "Quantity" to "मात्रा", "Unit" to "इकाई",
    "Amount" to "रकम", "Currency" to "मुद्रा", "Purchase date" to "खरीद की तारीख", "Applied on" to "डालने की तारीख",
    "Work date" to "काम की तारीख", "Number of workers" to "मजदूरों की संख्या", "Hours" to "घंटे", "Work description" to "काम का विवरण",
    "Service" to "सेवा", "Provider" to "सेवा देने वाला", "What was the expense?" to "किस बात का खर्च हुआ?",
    "Category" to "श्रेणी", "Expense date" to "खर्च की तारीख", "Unit price" to "इकाई का दाम", "Harvest date" to "कटाई की तारीख",
    "Bigha" to "बीघा", "Kattha" to "कट्ठा", "Dhur" to "धुर", "Acre" to "एकड़", "Hectare" to "हेक्टेयर", "Square metre" to "वर्ग मीटर",
    "Grade" to "गुणवत्ता", "Sale date" to "बिक्री की तारीख", "Price per unit" to "हर इकाई का दाम", "Total amount" to "कुल रकम",
    "Buyer" to "खरीदार", "Observed on" to "भाव दर्ज करने की तारीख", "Market" to "बाज़ार", "Price unit" to "भाव की इकाई",
    "Price" to "भाव", "Source" to "स्रोत", "Category name" to "श्रेणी का नाम", "Custom category" to "अपनी श्रेणी",
    "Welcome back" to "वापसी पर स्वागत है", "Sign in" to "साइन इन करें", "Create account" to "खाता बनाएँ",
    "Create your account" to "अपना खाता बनाएँ", "Email address" to "ईमेल पता", "Password" to "पासवर्ड",
    "Forgot password?" to "पासवर्ड भूल गए?", "Send recovery link" to "पासवर्ड बदलने का लिंक भेजें", "Show password" to "पासवर्ड दिखाएँ",
    "Hide password" to "पासवर्ड छिपाएँ", "New password" to "नया पासवर्ड", "Confirm password" to "पासवर्ड की पुष्टि करें",
    "Confirm new password" to "नए पासवर्ड की पुष्टि करें", "Save new password" to "नया पासवर्ड सहेजें",
    "Reset your password" to "अपना पासवर्ड बदलें", "Choose a new password" to "नया पासवर्ड चुनें",
    "Family name" to "परिवार का नाम", "Create family" to "परिवार बनाएँ", "Join family" to "परिवार से जुड़ें",
    "Join a family" to "परिवार से जुड़ें", "Invitation code" to "निमंत्रण कोड", "Choose your family" to "अपना परिवार चुनें",
    "Sign out" to "साइन आउट करें", "Automatic night theme" to "रात में थीम अपने आप बदलें", "Dark theme" to "डार्क थीम", "Hindi language" to "हिंदी भाषा",
    "PENDING" to "बाकी", "SYNCING" to "सिंक हो रहा है", "SYNCED" to "सिंक पूरा", "FAILED" to "सिंक विफल", "CONFLICT" to "टकराव",
    "Your name" to "आपका नाम", "Save account" to "खाता सहेजें", "Choose profile picture" to "प्रोफ़ाइल फ़ोटो चुनें",
    "Change profile picture" to "प्रोफ़ाइल फ़ोटो बदलें", "Remove picture" to "फ़ोटो हटाएँ", "Profile picture" to "प्रोफ़ाइल फ़ोटो",
    "Create invitation" to "निमंत्रण बनाएँ", "Invite a family member" to "परिवार के सदस्य को बुलाएँ", "Copy link" to "लिंक कॉपी करें",
    "Share link" to "लिंक साझा करें", "Share family invitation" to "परिवार का निमंत्रण साझा करें", "Invitation link or code" to "निमंत्रण लिंक या कोड",
    "Shareable invitation link" to "साझा करने योग्य निमंत्रण लिंक", "Revoke invitation" to "निमंत्रण रद्द करें",
    "Family invitation ready. Sign in or create an account and you will join automatically. No owner approval is needed." to "परिवार का निमंत्रण तैयार है। साइन इन करें या खाता बनाएँ; आप अपने आप जुड़ जाएँगे। परिवार मालिक की मंज़ूरी की ज़रूरत नहीं है।",
    "This invitation link is not valid. Ask the family owner for a new link." to "यह निमंत्रण लिंक मान्य नहीं है। परिवार मालिक से नया लिंक माँगें।",
    "Could not join this family" to "परिवार से नहीं जुड़ सके", "Try invitation again" to "निमंत्रण फिर आज़माएँ",
    "Continue without joining" to "बिना जुड़े जारी रखें", "Joining your family" to "आपके परिवार से जुड़ रहे हैं",
    "Manage access" to "पहुंच संभालें", "Restore access" to "पहुंच वापस दें", "Add records" to "रिकॉर्ड जोड़ें",
    "Edit records" to "रिकॉर्ड बदलें", "Delete records" to "रिकॉर्ड हटाएँ", "Can view" to "देख सकते हैं",
    "Download" to "डाउनलोड करें", "Review restore" to "पुनर्स्थापना की जाँच करें", "Confirm restore" to "पुनर्स्थापना की पुष्टि करें",
    "Create CSV export" to "CSV फ़ाइल बनाएँ", "Create restorable ZIP backup" to "वापस लाने योग्य ZIP बैकअप बनाएँ",
    "Available backups" to "उपलब्ध बैकअप", "AVAILABLE BACKUPS" to "उपलब्ध बैकअप",
    "No fields yet" to "अभी कोई खेत नहीं है", "No report data yet" to "इस अवधि का कोई रिपोर्ट डेटा नहीं है",
    "No matching records" to "कोई मिलता-जुलता रिकॉर्ड नहीं मिला", "No record type found" to "कोई रिकॉर्ड प्रकार नहीं मिला",
    "This year" to "इस साल", "At a glance" to "एक नज़र में", "CURRENT SEASON" to "मौजूदा मौसम",
    "LAND & SEASONS" to "खेत और मौसम", "FARM WORK" to "खेती का काम", "EXPLORE REPORTS" to "रिपोर्ट देखें",
    "Farm records" to "खेती के रिकॉर्ड", "Keep each season’s work connected to the right fields and crops." to "हर मौसम का काम सही खेत और फसल से जोड़कर रखें।",
    "A clear view of the work your family has recorded." to "आपके परिवार के दर्ज किए गए काम का साफ़ हिसाब।",
    "Add your first field to begin tracking farm work." to "खेती का काम दर्ज करना शुरू करने के लिए अपना पहला खेत जोड़ें।",
    "Add a field to start a season and keep the family’s records together." to "मौसम का काम और परिवार के रिकॉर्ड जोड़ने के लिए खेत दर्ज करें।",
    "Add a field" to "खेत जोड़ें", "Find a record type" to "रिकॉर्ड का प्रकार खोजें", "Search farm records" to "खेती के रिकॉर्ड खोजें",
    "Ask about your farm" to "अपने खेत के बारे में पूछें", "Ask Farm AI" to "खेती सहायक से पूछें",
    "What would you like to know?" to "आप क्या जानना चाहते हैं?", "For example: summarize this year’s expenses" to "उदाहरण: इस साल के खर्च का सार बताएँ",
    "Expense analysis" to "खर्च का विश्लेषण", "Harvest analysis" to "कटाई का विश्लेषण", "Sales analysis" to "बिक्री का विश्लेषण",
    "Crop performance" to "फसल का प्रदर्शन", "Field performance" to "खेत का प्रदर्शन", "Field details" to "खेत की जानकारी",
    "Growing seasons" to "खेती के मौसम", "Expenses recorded" to "दर्ज खर्च", "Harvest recorded" to "दर्ज कटाई",
    "Sales recorded" to "दर्ज बिक्री", "Profit" to "मुनाफ़ा", "Recorded value comparison" to "दर्ज रकम की तुलना",
    "No matching records" to "कोई मिलता-जुलता रिकॉर्ड नहीं मिला", "Not available" to "उपलब्ध नहीं",
    "Weather hasn’t been requested yet." to "मौसम की जानकारी अभी नहीं माँगी गई है।", "Getting local weather…" to "स्थानीय मौसम की जानकारी ली जा रही है…",
    "Loading family reports…" to "परिवार की रिपोर्ट लोड हो रही है…", "Checking family records…" to "परिवार के रिकॉर्ड जाँचे जा रहे हैं…",
    "Searching your family records…" to "परिवार के रिकॉर्ड खोजे जा रहे हैं…", "Saving securely…" to "सुरक्षित रूप से सहेजा जा रहा है…",
    "Try another date range or add a record." to "दूसरी तारीखें चुनें या नया रिकॉर्ड जोड़ें।",
    "The start date must be on or before the end date." to "शुरू की तारीख, आखिरी तारीख के बाद नहीं हो सकती।",
    "This record changed on another device" to "यह रिकॉर्ड किसी दूसरे डिवाइस पर बदल गया है",
    "Your family records remain saved." to "आपके परिवार के रिकॉर्ड सुरक्षित हैं।", "FARM FAMILY" to "FARM FAMILY"
    ,"A shared place for your farm" to "आपके खेत के काम के लिए साझा जगह",
    "Already have an account?" to "क्या आपका खाता पहले से है?", "Back to sign in" to "साइन इन पर वापस जाएँ",
    "Create a family workspace or join one with an invitation code." to "परिवार की जगह बनाएँ या निमंत्रण कोड से जुड़ें।",
    "Enter a name for your family." to "अपने परिवार का नाम लिखें।", "Enter a valid email address." to "सही ईमेल पता लिखें।",
    "Enter the invitation code you received." to "मिला हुआ निमंत्रण कोड लिखें।", "Enter your name." to "अपना नाम लिखें।",
    "Enter your password." to "अपना पासवर्ड लिखें।", "Family name must be 160 characters or fewer." to "परिवार का नाम 160 अक्षरों से छोटा रखें।",
    "Family records are shared only with members you invite." to "परिवार के रिकॉर्ड केवल आपके बुलाए सदस्य देख सकते हैं।",
    "Forgot password?" to "पासवर्ड भूल गए?", "Grow together. Prosper together." to "साथ बढ़ें, साथ खुशहाल बनें।",
    "New to FARM FAMILY?" to "FARM FAMILY पर नए हैं?", "Select the farm workspace you want to open." to "खोलने के लिए परिवार का खेत चुनें।",
    "Sign in to see your family’s farm records." to "परिवार के खेती के रिकॉर्ड देखने के लिए साइन इन करें।",
    "Start a shared place for your family’s farm work." to "परिवार के खेती के काम के लिए साझा जगह बनाएँ।",
    "The passwords do not match." to "दोनों पासवर्ड मेल नहीं खाते।", "Use at least 8 characters for your password." to "पासवर्ड में कम से कम 8 अक्षर रखें।",
    "Use at least 8 characters." to "कम से कम 8 अक्षर लिखें।", "We’ll send a secure recovery link if an account matches." to "खाता मिलने पर हम सुरक्षित लिंक भेजेंगे।",
    "Your email recovery link is verified. Set a new password to continue." to "ईमेल का लिंक सही है। आगे बढ़ने के लिए नया पासवर्ड बनाएँ।",
    "Your family’s information stays private to your authorized family members." to "आपके परिवार की जानकारी केवल अनुमति वाले सदस्यों को दिखती है।",
    "Sign out of FARM FAMILY?" to "क्या FARM FAMILY से साइन आउट करें?",
    "Your saved family records will stay in your account." to "परिवार के सहेजे रिकॉर्ड आपके खाते में रहेंगे।",
    "We couldn’t load your family" to "परिवार की जानकारी नहीं खुल सकी", "Growing together" to "साथ मिलकर आगे बढ़ें",
    "Loading your family" to "परिवार की जानकारी लोड हो रही है", "Preparing your farm" to "आपका खेत तैयार हो रहा है",
    "Changes saved." to "बदलाव सहेज दिए गए।", "Access removed" to "पहुंच हटा दी गई",
    "Choose who can view, add, change, or remove shared records." to "चुनें कि साझा रिकॉर्ड कौन देख, जोड़, बदल या हटा सकता है।",
    "Deleted family records stay here until restored. Only family members with delete access can restore them." to "हटाए गए रिकॉर्ड यहाँ रहेंगे। हटाने की अनुमति वाला सदस्य इन्हें वापस ला सकता है।",
    "Deleted records from your family will appear here." to "परिवार से हटाए गए रिकॉर्ड यहाँ दिखेंगे।",
    "Create CSV export" to "CSV फ़ाइल बनाएँ", "Create restorable ZIP backup" to "वापस लाने योग्य ZIP बैकअप बनाएँ",
    "Download a copy or restore a family backup after reviewing its contents." to "परिवार का बैकअप डाउनलोड करें या जाँच के बाद उसे वापस लाएँ।",
    "Import a ZIP backup" to "ZIP बैकअप से डेटा लाएँ", "Invite someone to join this family workspace." to "किसी को इस परिवार से जुड़ने का निमंत्रण भेजें।",
    "Expires in days (1–30)" to "कितने दिनों में समाप्त होगा (1–30)", "Family activity notifications will appear here." to "परिवार के काम की सूचनाएँ यहाँ दिखेंगी।",
    "Recycle bin is empty" to "रीसायकल बिन खाली है", "Restore completed. Review your records for any reported conflicts." to "रिकॉर्ड वापस आ गए। बताई गई टकराव की जानकारी जाँचें।",
    "This form is not available." to "यह फ़ॉर्म उपलब्ध नहीं है।", "This record type could not be opened." to "यह रिकॉर्ड प्रकार नहीं खुल सका।",
    "This report is not available." to "यह रिपोर्ट उपलब्ध नहीं है।", "This screen is unavailable" to "यह स्क्रीन उपलब्ध नहीं है।",
    "This section is not available." to "यह भाग उपलब्ध नहीं है।", "Unknown record type" to "रिकॉर्ड का प्रकार पता नहीं है",
    "Go back and choose another section." to "वापस जाकर दूसरा भाग चुनें।", "No record type found" to "कोई रिकॉर्ड प्रकार नहीं मिला",
    "No fields yet" to "अभी कोई खेत नहीं है", "No report data yet" to "अभी रिपोर्ट का डेटा नहीं है",
    "No totals yet" to "अभी कोई कुल रकम नहीं है", "Add the first record for this family." to "इस परिवार का पहला रिकॉर्ड जोड़ें।",
    "Add a field to start a season and keep the family’s records together." to "मौसम का काम शुरू करने और पारिवारिक रिकॉर्ड जोड़ने के लिए खेत दर्ज करें।",
    "Add field coordinates" to "खेत की जगह के निर्देशांक जोड़ें", "Add latitude and longitude to a field to see local conditions." to "स्थानीय मौसम देखने के लिए खेत का अक्षांश और देशांतर भरें।",
    "Add saved farm records to build a report." to "रिपोर्ट बनाने के लिए खेती के रिकॉर्ड जोड़ें।",
    "A clear view of the work your family has recorded." to "परिवार के दर्ज किए गए काम का साफ़ विवरण।",
    "Answers use your family’s saved records and approved reports." to "जवाब परिवार के सहेजे रिकॉर्ड और रिपोर्ट पर आधारित हैं।",
    "Based on your family’s authorized farm data." to "परिवार की अनुमति वाली खेती की जानकारी पर आधारित।",
    "Farm AI is read-only. It cannot make or change records." to "कृषि सहायक केवल जानकारी देता है। यह रिकॉर्ड नहीं बदल सकता।",
    "Keep your question under 2,000 characters." to "अपना सवाल 2,000 अक्षरों से छोटा रखें।",
    "Measurement units can’t be combined" to "अलग-अलग माप इकाइयों को जोड़ा नहीं जा सकता",
    "Not available until costs and revenue are marked complete" to "खर्च और कमाई पूरी दर्ज होने के बाद उपलब्ध होगा",
    "Profit is available only after the family marks crop-season costs and revenue complete." to "मुनाफ़ा तभी दिखेगा जब मौसम के खर्च और कमाई पूरी दर्ज हों।",
    "There aren’t compatible currency totals to chart yet." to "चार्ट बनाने के लिए अभी मिलती-जुलती मुद्रा का कुल उपलब्ध नहीं है।",
    "Totals can’t be combined safely" to "कुल रकम को सुरक्षित रूप से जोड़ा नहीं जा सकता",
    "Weather hasn’t been requested yet." to "मौसम की जानकारी अभी नहीं माँगी गई है।",
    "Getting local weather…" to "स्थानीय मौसम की जानकारी ली जा रही है…",
    "Loading family reports…" to "परिवार की रिपोर्ट लोड हो रही है…", "Checking family records…" to "परिवार के रिकॉर्ड जाँचे जा रहे हैं…",
    "Loading deleted records…" to "हटाए गए रिकॉर्ड लोड हो रहे हैं…", "Loading family access…" to "परिवार की पहुंच लोड हो रही है…",
    "Loading notifications…" to "सूचनाएँ लोड हो रही हैं…", "Loading report…" to "रिपोर्ट लोड हो रही है…",
    "Searching your family records…" to "परिवार के रिकॉर्ड खोजे जा रहे हैं…", "Saving profile picture…" to "प्रोफ़ाइल फ़ोटो सहेजी जा रही है…",
    "Saving securely…" to "सुरक्षित रूप से सहेजा जा रहा है…", "Moving record to recycle history…" to "रिकॉर्ड रीसायकल बिन में भेजा जा रहा है…",
    "Record moved to the family recycle history." to "रिकॉर्ड परिवार के रीसायकल बिन में भेज दिया गया।",
    "Record not found" to "रिकॉर्ड नहीं मिला", "Record unavailable" to "रिकॉर्ड उपलब्ध नहीं है",
    "You’re all caught up" to "सब कुछ नया है", "Activity and sync updates" to "काम और सिंक की जानकारी",
    "Switch or refresh family" to "परिवार बदलें या जानकारी फिर लोड करें",
    "Shared family data is protected by membership permissions and server side access policies." to "परिवार की साझा जानकारी सदस्य की अनुमति और सुरक्षित नियमों से बचाई जाती है।",
    "Family permissions are enforced by Supabase as well as by the controls shown in the app." to "परिवार की अनुमति ऐप और सुरक्षित सर्वर, दोनों पर लागू होती है।",
    "Your family role does not grant access to farm records." to "आपकी परिवार भूमिका से खेती के रिकॉर्ड देखने की अनुमति नहीं है।",
    "Your family’s access rules are checked again when this record is saved." to "यह रिकॉर्ड सहेजते समय परिवार की अनुमति फिर जाँची जाएगी।",
    "Your family can review or restore it later if recovery is enabled." to "वापसी की सुविधा चालू होने पर परिवार इसे बाद में देख या वापस ला सकता है।",
    "Try another date range or add a record." to "दूसरी तारीखें चुनें या रिकॉर्ड जोड़ें।", "Try another word or a shorter phrase." to "दूसरा शब्द या छोटा वाक्यांश आज़माएँ।",
    "Try a shorter search." to "खोज छोटा करके देखें।", "FARM FAMILY · Version 1.0" to "FARM FAMILY · संस्करण 1.0",
    "Hindi language" to "हिंदी भाषा", "Automatic night theme" to "रात में थीम अपने आप बदलें", "Family currency: NPR" to "परिवार की मुद्रा: NPR",
    "Owner" to "मालिक", "Editor" to "बदलाव करने वाला", "Viewer" to "देखने वाला",
    "OWNER" to "मालिक", "EDITOR" to "बदलाव करने वाला", "VIEWER" to "देखने वाला",
    "Back to sign in" to "साइन इन पर वापस जाएँ", "Save new password" to "नया पासवर्ड सहेजें",
    "Access removed" to "पहुंच हटा दी गई", "Clear" to "साफ़ करें", "Discard draft and return" to "मसौदा छोड़कर वापस जाएँ",
    "Expires in days (1–30)" to "कितने दिनों में समाप्त होगा (1–30)", "Import a ZIP backup" to "ZIP बैकअप से डेटा लाएँ",
    "Invitation code" to "निमंत्रण कोड", "Move record" to "रिकॉर्ड हटाएँ", "Move to recycle history" to "रीसायकल बिन में भेजें",
    "Move this record to recycle history?" to "क्या यह रिकॉर्ड रीसायकल बिन में भेजें?",
    "Record moved to the family recycle history." to "रिकॉर्ड परिवार के रीसायकल बिन में भेज दिया गया।",
    "Restore preview" to "वापस लाने से पहले की जानकारी", "Refresh backups" to "बैकअप फिर लोड करें",
    "Refresh invitations" to "निमंत्रण फिर लोड करें", "Search" to "खोजें", "Share" to "साझा करें",
    "Switch or refresh family" to "परिवार बदलें या जानकारी फिर लोड करें", "What would you like to add?" to "आप क्या जोड़ना चाहते हैं?",
    "Farm records" to "खेती के रिकॉर्ड", "Find a record type" to "रिकॉर्ड का प्रकार खोजें",
    "For example: summarize this year’s expenses" to "उदाहरण: इस साल के खर्च का सार बताएँ",
    "Keep each season’s work connected to the right fields and crops." to "हर मौसम का काम सही खेत और फसल से जोड़कर रखें।",
    "Ask about your farm" to "अपने खेत के बारे में पूछें", "Answers use your family’s saved records and approved reports." to "जवाब परिवार के सहेजे रिकॉर्ड और रिपोर्ट पर आधारित हैं।",
    "Based on your family’s authorized farm data." to "परिवार की अनुमति वाली खेती की जानकारी पर आधारित।",
    "Family permissions are enforced by Supabase as well as by the controls shown in the app." to "परिवार की अनुमति ऐप और सुरक्षित सर्वर, दोनों पर लागू होती है।",
    "Your family’s information stays private to your authorized family members." to "आपके परिवार की जानकारी केवल अनुमति वाले सदस्यों को दिखती है।",
    "A clear view of the work your family has recorded." to "परिवार के दर्ज किए गए काम का साफ़ विवरण।",
    "Add your first field to begin tracking farm work." to "खेती का काम दर्ज करना शुरू करने के लिए अपना पहला खेत जोड़ें।",
    "CURRENT SEASON" to "मौजूदा मौसम", "EXPLORE REPORTS" to "रिपोर्ट देखें", "FARM WORK" to "खेती का काम",
    "LAND & SEASONS" to "खेत और मौसम", "YEARLY SUMMARY" to "साल का सार",
    "Sign in" to "साइन इन करें", "Create account" to "खाता बनाएँ", "Join family" to "परिवार से जुड़ें",
    "A shared place for your family’s farm work." to "परिवार के खेती के काम के लिए साझा जगह।",
    "All family records are private to invited members." to "परिवार के रिकॉर्ड केवल बुलाए गए सदस्यों को दिखते हैं।",
    "You’re all caught up" to "सब कुछ नया है", "This record changed on another device" to "यह रिकॉर्ड किसी दूसरे डिवाइस पर बदल गया है",
    "Review the latest version before continuing. Your unsaved form will stay open until you discard it." to "जारी रखने से पहले नया रूप देखें। आपका अधूरा फ़ॉर्म छोड़ने तक खुला रहेगा।",
    "Your family’s access rules are checked again when this record is saved." to "रिकॉर्ड सहेजते समय परिवार की अनुमति फिर जाँची जाएगी।",
    "Choose who can view, add, change, or remove shared records." to "चुनें कि साझा रिकॉर्ड कौन देख, जोड़, बदल या हटा सकता है।",
    "Invite someone to join this family workspace." to "किसी को इस परिवार में जुड़ने का निमंत्रण भेजें।",
    "Create CSV export" to "CSV फ़ाइल बनाएँ", "Create restorable ZIP backup" to "वापस लाने योग्य ZIP बैकअप बनाएँ",
    "Download a copy or restore a family backup after reviewing its contents." to "परिवार का बैकअप डाउनलोड करें या जाँच के बाद वापस लाएँ।",
    "No view" to "देखने की अनुमति नहीं", "Not confirmed" to "पुष्टि नहीं हुई", "Yes" to "हाँ",
    "The passwords do not match." to "दोनों पासवर्ड मेल नहीं खाते।", "Use at least 8 characters." to "कम से कम 8 अक्षर लिखें।",
    "Use at least 8 characters for your password." to "पासवर्ड में कम से कम 8 अक्षर रखें।",
    "No record type found" to "कोई रिकॉर्ड प्रकार नहीं मिला", "Add a field" to "खेत जोड़ें",
    "Add field coordinates" to "खेत की जगह के निर्देशांक जोड़ें", "Add latitude and longitude to a field to see local conditions." to "स्थानीय मौसम देखने के लिए खेत का अक्षांश और देशांतर भरें।",
    "Add saved farm records to build a report." to "रिपोर्ट बनाने के लिए खेती के रिकॉर्ड जोड़ें।",
    "A shared place for your farm" to "आपके खेत के काम के लिए साझा जगह",
    "Go back and choose another section." to "वापस जाकर दूसरा भाग चुनें।", "This form is not available." to "यह फ़ॉर्म उपलब्ध नहीं है।",
    "This record type could not be opened." to "यह रिकॉर्ड प्रकार नहीं खुल सका।", "This report is not available." to "यह रिपोर्ट उपलब्ध नहीं है।",
    "This screen is unavailable" to "यह स्क्रीन उपलब्ध नहीं है।", "This section is not available." to "यह भाग उपलब्ध नहीं है।",
    "FARM FAMILY · Version 1.0" to "FARM FAMILY · संस्करण 1.0"
)

internal fun farmLabel(english: String, hindi: Boolean): String = if (hindi) translateFarmText(english, true) else english

internal fun translateFarmText(text: String, hindi: Boolean): String {
    if (!hindi) return text
    HindiLabels[text]?.let { return it }
    return when {
        text.startsWith("Good day, ") -> "नमस्ते, " + text.removePrefix("Good day, ")
        text.startsWith("Add ") && text.length > 4 -> "${HindiLabels[text.removePrefix("Add ").lowercase()] ?: text.removePrefix("Add ")} जोड़ें"
        text.startsWith("Edit ") && text.length > 5 -> "${HindiLabels[text.removePrefix("Edit ").lowercase()] ?: text.removePrefix("Edit ")} बदलें"
        text.startsWith("Loading ") -> text.removePrefix("Loading ").removeSuffix("…") + " लोड हो रहा है…"
        text.startsWith("No ") && text.endsWith(" yet") -> "अभी कोई रिकॉर्ड नहीं है।"
        text.startsWith("Enter ") && text.endsWith(" is required.") -> "यह जानकारी भरना ज़रूरी है।"
        text.startsWith("Family currency: ") -> "परिवार की मुद्रा: " + text.removePrefix("Family currency: ")
        text.startsWith("Choose ") -> "चुनें: " + text.removePrefix("Choose ")
        text.startsWith("No ") && text.endsWith(" have been added yet.") -> "अभी कोई संबंधित जानकारी नहीं जोड़ी गई है।"
        text.startsWith("Family records · ") -> "परिवार के रिकॉर्ड · " + text.removePrefix("Family records · ")
        text.startsWith("Profit · ") -> "मुनाफ़ा · " + text.removePrefix("Profit · ")
        text.startsWith("YEARLY SUMMARY · ") -> "साल का सार · " + text.removePrefix("YEARLY SUMMARY · ")
        text.startsWith("You opened version ") -> "आपने संस्करण " + text.removePrefix("You opened version ")
        text.startsWith("Invitation code: ") -> "निमंत्रण कोड: " + text.removePrefix("Invitation code: ")
        text.startsWith("Latest saved values: ") -> "दूसरे डिवाइस पर सहेजी जानकारी: " + text.removePrefix("Latest saved values: ")
        text.startsWith("Expires ") -> "समाप्ति: " + text.removePrefix("Expires ")
        text.endsWith(" backup") -> "बैकअप · " + text.removeSuffix(" backup")
        text.endsWith(" records in this report") -> text.removeSuffix(" records in this report") + " रिकॉर्ड इस रिपोर्ट में"
        text.endsWith(" analysis") -> (HindiLabels[text.removeSuffix(" analysis")] ?: text.removeSuffix(" analysis")) + " का विश्लेषण"
        text.startsWith("Year: ") -> "साल: " + text.removePrefix("Year: ")
        text.contains(" · ") -> text.split(" · ").joinToString(" · ") { HindiLabels[it] ?: it }
        else -> text
    }
}

@Composable
internal fun FarmText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = translateFarmText(text, LocalFarmHindi.current), modifier = modifier, color = color,
        fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}
