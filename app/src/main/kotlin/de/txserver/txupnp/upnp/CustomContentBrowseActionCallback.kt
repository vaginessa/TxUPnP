package de.txserver.txupnp.upnp

import android.content.Context
import android.content.SharedPreferences

import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.model.action.ActionException
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.model.types.ErrorCode
import org.fourthline.cling.support.contentdirectory.callback.Browse
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.DIDLObject
import org.fourthline.cling.support.model.SortCriterion
import org.fourthline.cling.support.model.item.Item

import java.net.URI
import java.util.ArrayList

import de.txserver.txupnp.R
import de.txserver.txupnp.app.TxUPnP
import de.txserver.txupnp.helper.ItemModel
import de.txserver.txupnp.helper.MimeTypeMap

class CustomContentBrowseActionCallback @JvmOverloads constructor(private val context: Context, private val handler: ContentDirectoryBrowseHandler, private val androidUpnpService: AndroidUpnpService, private val service: Service<*, *>, private val id: String, private val firstResult: Long = 0L) : Browse(service, id, BrowseFlag.DIRECT_CHILDREN, "*", firstResult, 99999L, SortCriterion(true, "dc:title")) {
    private val prefs: SharedPreferences

    init {
        prefs = TxUPnP.instance.getSharedPref()
    }

    private fun createItemModel(item: DIDLObject): ItemModel {

        val itemModel = ItemModel(context.resources,
                R.drawable.ic_folder_black_24px, service, item)

        var usableIcon: URI? = item.getFirstPropertyValue(DIDLObject.Property.UPNP.ICON::class.java)
        if (usableIcon == null || usableIcon.toString().isEmpty()) {
            usableIcon = item.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM_ART_URI::class.java)
        }
        if (usableIcon != null)
            itemModel.iconUrl = usableIcon.toString()

        if (item is Item) {
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(itemModel.url))

            if (mimeType!!.contains("video")) {
                itemModel.icon = R.drawable.ic_file_video_black_24px

            } else if (mimeType.contains("audio")) {
                itemModel.icon = R.drawable.ic_file_music_black_24px

            } else if (mimeType.contains("image")) {
                itemModel.icon = R.drawable.ic_file_image_black_24px

            } else {
                itemModel.icon = R.drawable.ic_file_black_24px
            }

            if (prefs.getBoolean("settings_hide_file_icons", false))
                itemModel.hideIcon = true

            if (prefs.getBoolean("settings_show_extensions", false))
                itemModel.setShowExtension(true)
        }

        return itemModel
    }

    override fun received(actionInvocation: ActionInvocation<*>, didl: DIDLContent) {

        val items = ArrayList<ItemModel>()
        val totalMatches: Long

        try {
            for (childContainer in didl.containers)
                items.add(createItemModel(childContainer))

            for (childItem in didl.items)
                items.add(createItemModel(childItem))

            totalMatches = java.lang.Long.parseLong(actionInvocation.getOutput("TotalMatches").toString())

            if (firstResult < 99999L && (firstResult + items.size) < totalMatches) {

                handler.browseRequired(service, id, firstResult + items.size);

            } else {
                handler.browseFinished()
            }

            if (firstResult == 0L) {
                handler.onDisplayItems(items)
            } else {
                handler.onDisplayAddItems(items)
            }

        } catch (ex: Exception) {
            actionInvocation.failure = ActionException(
                    ErrorCode.ACTION_FAILED,
                    "Can't create list childs: $ex", ex)
            failure(actionInvocation, null, ex.message!!)
        }

    }

    override fun updateStatus(status: Browse.Status) {

    }

    override fun failure(invocation: ActionInvocation<*>, response: UpnpResponse?, s: String) {
        handler.onDisplayItemsError(createDefaultFailureMessage(invocation, response))
        handler.browseFinished()
    }
}
