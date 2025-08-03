/*
    Copyright (C) 2019 Ilya Zhuravlev

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package mods

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.util.TypedValue

import com.libopenmw.openmw.R
import android.graphics.*
import android.view.View
/**
 * Callback for dragging the mods around to change load order
 */
class ModMoveCallback(private val mAdapter: ModsAdapter, private val mPluginAdapter: ModsAdapter, private val mGroundcoverAdapter: ModsAdapter) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean {
        return true
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return true
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue * 7f
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return 0.6f
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
        val type = mAdapter.collection.mods[viewHolder.adapterPosition].type

        if (type == ModType.Plugin) {
            mAdapter.collection.mods[viewHolder.adapterPosition].type = ModType.Groundcover
            mAdapter.collection.mods[viewHolder.adapterPosition].order = mGroundcoverAdapter.collection.mods.size + 1
            mGroundcoverAdapter.collection.mods.add(mAdapter.collection.mods[viewHolder.adapterPosition])
            mGroundcoverAdapter.notifyItemInserted(mGroundcoverAdapter.collection.mods.size)
            mGroundcoverAdapter.notifyDataSetChanged()
        }
        else if (type == ModType.Groundcover) {
            mAdapter.collection.mods[viewHolder.adapterPosition].type = ModType.Plugin
            mAdapter.collection.mods[viewHolder.adapterPosition].order = mPluginAdapter.collection.mods.size + 1
            mPluginAdapter.collection.mods.add(mAdapter.collection.mods[viewHolder.adapterPosition])
            mPluginAdapter.notifyItemInserted(mPluginAdapter.collection.mods.size)
            mPluginAdapter.notifyDataSetChanged()
        }

        mAdapter.collection.mods.removeAt(viewHolder.adapterPosition)
        mAdapter.notifyItemRemoved(viewHolder.adapterPosition)
        mAdapter.notifyDataSetChanged()
    }

    override fun getMovementFlags(recyclerView: RecyclerView,
                                  viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        var swipeFlags = if (mAdapter.collection.mods[viewHolder.adapterPosition].type == ModType.Resource) 0 else ItemTouchHelper.END

        return ItemTouchHelper.Callback.makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder): Boolean {
        mAdapter.onRowMoved(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?,
                                   actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder is ModsAdapter.ModViewHolder) {
                mAdapter.onRowSelected(viewHolder)
            }
        }

        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView,
                           viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        if (viewHolder is ModsAdapter.ModViewHolder) {
            mAdapter.onRowClear(viewHolder)
        }
    }

    /**
     * Speed up dragging of a list element
     */
    override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView, viewSize: Int,
                                              viewSizeOutOfBounds: Int, totalSize: Int,
                                              msSinceStartScroll: Long): Int {
        val direction = Math.signum(viewSizeOutOfBounds.toFloat()).toInt()
        return 20 * direction
    }

    private val paint = Paint()

    override  fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val type = mAdapter.collection.mods[viewHolder.adapterPosition].type
        val resources = recyclerView.getContext().getResources()
        val icon: Bitmap

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView: View = viewHolder.itemView
            val height = itemView.bottom.toFloat() - itemView.top.toFloat()
            val width = height / 3
            var color = "#D34F4F"
            var text = "REMOVE"
            var drawable = R.drawable.ic_delete

            if (type == ModType.Plugin) {
                color = "#4FD34F"
                text = "AS GROUNDCOVER"
                drawable = R.drawable.ic_grass
            }
            else if (type == ModType.Groundcover) {
                color = "#4F4FD3"
                text = "AS PLUGIN"
                drawable = R.drawable.ic_file
            }

            if (dX > 0) {
                paint.color = Color.parseColor(color)
                val background = RectF(itemView.left.toFloat(), itemView.top.toFloat(), dX, itemView.bottom.toFloat())
                c.drawRect(background, paint)
                icon = BitmapFactory.decodeResource(resources, drawable)
                val iconDest = RectF(itemView.left.toFloat() + (width / 2f), itemView.top.toFloat() + (width / 2f), itemView.left.toFloat() + 2.5f * width, itemView.bottom.toFloat() - (width / 2f))
                c.drawBitmap(icon, null, iconDest, paint)
                paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 17f, resources.displayMetrics))
                paint.color = Color.parseColor("#222222")
                c.drawText(text, itemView.left.toFloat() + (width * 3f), itemView.bottom.toFloat() - width, paint)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

}
