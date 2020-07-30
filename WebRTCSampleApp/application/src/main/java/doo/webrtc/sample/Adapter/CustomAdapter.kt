package doo.webrtc.sample.Adapter

import android.util.TypedValue
import android.R
import android.content.Context
import android.widget.TextView
import android.support.v7.widget.RecyclerView
import android.view.*


class CustomAdapter(private val context: Context, private val mList: List<String>?) :
    RecyclerView.Adapter<CustomAdapter.CustomViewHolder>() {

    private val TOKEN = "/"

    inner class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var text: TextView

        init {
            this.text = view.findViewById(R.id.text1)
        }

    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        // Handle key up and key down and attempt to move selection
        recyclerView.setOnKeyListener(object : View.OnKeyListener{
            override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
                val lm = recyclerView.layoutManager

                // Return false if scrolled to the bounds and allow focus to move off the list
                if (event.action === KeyEvent.ACTION_DOWN) {
//                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
//                        return tryMoveSelection(lm, 1)
//                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
//                        return tryMoveSelection(lm, -1)
//                    }
                }

                return false
            }
        })
    }

    // RecyclerView에 새로운 데이터를 보여주기 위해 필요한 ViewHolder를 생성해야 할 때 호출됩니다.
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CustomViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.simple_list_item_1, viewGroup, false)

        return CustomViewHolder(view)
    }


    // Adapter의 특정 위치(position)에 있는 데이터를 보여줘야 할때 호출됩니다.
    override fun onBindViewHolder(viewholder: CustomViewHolder, position: Int) {
        viewholder.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        viewholder.text.gravity = Gravity.LEFT
//        viewholder.text.setText("${mList?.get(position)!!.ip}:${mList?.get(position).port}$TOKEN${mList?.get(position).userName}$TOKEN${mList?.get(position).confidential}")

        viewholder.text.text = mList?.get(position)
    }

    override fun getItemCount(): Int {
        return mList!!.size
    }

}