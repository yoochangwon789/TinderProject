package com.yoochangwonspro.tinderproject

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.yoochangwonspro.tinderproject.DBKey.Companion.DIS_LIKE
import com.yoochangwonspro.tinderproject.DBKey.Companion.LIKE
import com.yoochangwonspro.tinderproject.DBKey.Companion.LIKED_BY
import com.yoochangwonspro.tinderproject.DBKey.Companion.MATCH
import com.yoochangwonspro.tinderproject.DBKey.Companion.NAME
import com.yoochangwonspro.tinderproject.DBKey.Companion.USERS
import com.yoochangwonspro.tinderproject.DBKey.Companion.USER_ID
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.CardStackView
import com.yuyakaido.android.cardstackview.Direction

class LikeActivity : AppCompatActivity(), CardStackListener {

    private var auth: FirebaseAuth = FirebaseAuth.getInstance()
    lateinit var userDB: DatabaseReference

    private val adapter = CardItemAdapter()
    private val cardItems = mutableListOf<CardItem>()
    private val manager by lazy {
        CardStackLayoutManager(this, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_like)

        userDB = Firebase.database.reference.child(USERS)

        val currentUserDB = userDB.child(getCurrentUserID())
        currentUserDB.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child(NAME).value == null) {
                    showNameInputPopup()
                    return
                }

                getUnSelectedUsers()
                // 유저 정보를 갱신해라
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        initCardStackView()
        initLogoutButton()
        initMatchedListButton()
        Log.d("CardItem", "$cardItems")
    }

    private fun initLogoutButton() {
        val signOutButton = findViewById<Button>(R.id.logoutButton)
        signOutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun initMatchedListButton() {
        val matchedListButton = findViewById<Button>(R.id.matchListButton)
        matchedListButton.setOnClickListener {
            startActivity(Intent(this, MatchedUserActivity::class.java))
        }
    }

    private fun initCardStackView() {
        val stackView = findViewById<CardStackView>(R.id.cardStackView)
        stackView.layoutManager = manager
        stackView.adapter = adapter
    }

    private fun getUnSelectedUsers() {
        userDB.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // 아무것도 선택되지 않은 user 의 조건
                /*
                * 현재 보고있는 userId 가 나와 같지 않고
                * 나와 같지 않으면 나머진 모두 상대방
                * 상대방의 like 의 나의 userId 를 가지고 있지 않는 경우
                * 상대방의 disLike 의 나의 userId 를 자기고 있는 않는 경우
                * 즉 이 user 는 내가 한번도 선택한적이 없는 user 이다 라는 조건 성립
                * */
                if (snapshot.child(USER_ID).value != getCurrentUserID()
                    && snapshot.child(LIKED_BY).child(LIKE).hasChild(getCurrentUserID()).not()
                    && snapshot.child(LIKED_BY).child(DIS_LIKE).hasChild(getCurrentUserID()).not()
                ) {

                    val userId = snapshot.child(USER_ID).value.toString()
                    var name = "undecided"

                    if (snapshot.child(NAME).value != null) {
                        name = snapshot.child(NAME).value.toString()
                    }

                    cardItems.add(CardItem(userId, name))
                    adapter.submitList(cardItems)
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                /*
                * 데이터가 변경이 되었을 때 호출 된다.
                * 즉, userId 값으로 변경이 된 user 를 cardItems 에서 찾는다
                * 수정 된 name 값을 변경해주고
                * adapter 에게 다시 변경된 데이터값을 submitList 한다
                * */
                cardItems.find { it.userId == snapshot.key }?.let {
                    it.name = snapshot.child(NAME).value.toString()
                }

                adapter.submitList(cardItems)
                adapter.notifyDataSetChanged()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {}

        })
    }

    private fun showNameInputPopup() {
        val editText = EditText(this)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.write_name))
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                if (editText.text.isEmpty()) {
                    showNameInputPopup()
                } else {
                    saveUserName(editText.text.toString())
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun saveUserName(name: String) {
        val userId = getCurrentUserID()
        val currentUserDB = userDB.child(userId)
        val user = mutableMapOf<String, Any>()

        user[USER_ID] = userId
        user[NAME] = name
        currentUserDB.updateChildren(user)

        getUnSelectedUsers()
    }

    private fun getCurrentUserID(): String {
        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 되어있지 않습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        return auth.currentUser?.uid.orEmpty()
    }

    private fun like() {
        val card = cardItems[manager.topPosition - 1]
        cardItems.removeFirst()

        Log.d("CardItem", "$cardItems")

        userDB.child(card.userId)
            .child(LIKED_BY)
            .child(LIKE)
            .child(getCurrentUserID())
            .setValue(true)

        saveMatchIfOtherUserLikedMe(card.userId)

        Toast.makeText(this, "${card.name}님을 Like 하셨습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun saveMatchIfOtherUserLikedMe(otherUserId: String) {
        val otherUserDB =
            userDB.child(getCurrentUserID()).child(LIKED_BY).child(LIKE).child(otherUserId)

        otherUserDB.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.value == true) {
                    userDB.child(getCurrentUserID())
                        .child(LIKED_BY)
                        .child(MATCH)
                        .child(otherUserId)
                        .setValue(true)

                    userDB.child(otherUserId)
                        .child(LIKED_BY)
                        .child(MATCH)
                        .child(getCurrentUserID())
                        .setValue(true)
                }
            }

            override fun onCancelled(error: DatabaseError) {}

        })
    }

    private fun disLike() {
        val card = cardItems[manager.topPosition - 1]
        cardItems.removeFirst()

        userDB.child(card.userId)
            .child(LIKED_BY)
            .child(DIS_LIKE)
            .child(getCurrentUserID())
            .setValue(true)

        Toast.makeText(this, "${card.name}님을 disLike 하셨습니다.", Toast.LENGTH_SHORT).show()
    }

    override fun onCardSwiped(direction: Direction?) {
        when (direction) {
            Direction.Right -> like()
            Direction.Left -> disLike()
            else -> {
            }
        }
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}

    override fun onCardRewound() {}

    override fun onCardCanceled() {}

    override fun onCardAppeared(view: View?, position: Int) {}

    override fun onCardDisappeared(view: View?, position: Int) {}
}