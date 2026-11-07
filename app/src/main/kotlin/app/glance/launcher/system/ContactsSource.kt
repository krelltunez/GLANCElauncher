package app.glance.launcher.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import app.glance.launcher.ui.search.ContactHit

/**
 * The phone's contacts for search, read only when the user turned "Contacts in search" on and
 * granted the permission. Names only: search matches the name and opens the contact.
 */
object ContactsSource {
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Every visible contact with a name, or nothing without the permission (or on any read failure). */
    fun load(context: Context): List<ContactHit> {
        if (!hasPermission(context)) return emptyList()
        val out = ArrayList<ContactHit>()
        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, null,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val keyCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                val nameCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    out += ContactHit(c.getLong(idCol), c.getString(keyCol).orEmpty(), name)
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    /** The contact's own page in the phone's contacts app. */
    fun viewIntent(contact: ContactHit): Intent =
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The contact's number to call or text: the default one, else the first; null without one or the permission. */
    fun phoneNumber(context: Context, contact: ContactHit): String? {
        if (!hasPermission(context)) return null
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(contact.id.toString()),
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
        } catch (e: Exception) {
            null
        }
    }
}
