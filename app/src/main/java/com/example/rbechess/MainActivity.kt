package com.example.rbechess

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import android.media.MediaPlayer
import android.widget.TextView
import com.example.rbechess.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var mMediaPlayer: MediaPlayer? = null
    private lateinit var binding: ActivityMainBinding
    private var myNM = NextMoveObj()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
    }

    // FROM https://developer.android.com/training/keyboard-input/commands
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_D -> {
                Toast.makeText(applicationContext,"pressed the letter: D",Toast.LENGTH_SHORT).show()
                playSound()
                myNM.iterVar(fromToRankFile.FF)
                binding.myTextView.text = myNM.getNMOstr()
                true
            }
            KeyEvent.KEYCODE_F -> {
                Toast.makeText(applicationContext,"pressed the letter: F",Toast.LENGTH_SHORT).show()
                myNM.iterVar(fromToRankFile.FR)
                binding.myTextView.text = myNM.getNMOstr()
                true
            }
            KeyEvent.KEYCODE_J -> {
                Toast.makeText(applicationContext,"pressed the letter: J",Toast.LENGTH_SHORT).show()
                myNM.iterVar(fromToRankFile.TF)
                binding.myTextView.text = myNM.getNMOstr()
                true
            }
            KeyEvent.KEYCODE_K -> {
                Toast.makeText(applicationContext,"pressed the letter: K",Toast.LENGTH_SHORT).show()
                myNM.iterVar(fromToRankFile.TR)
                binding.myTextView.text = myNM.getNMOstr()
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    // FROM https://codersguidebook.com/how-to-create-an-android-app/play-sounds-music-android-app
    private fun playSound() {
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(this, R.raw.move)
            mMediaPlayer!!.isLooping = true
            mMediaPlayer!!.start()
        } else mMediaPlayer!!.start()
    }

    override fun onStop() {
        super.onStop()
        if (mMediaPlayer != null) {
            mMediaPlayer!!.release()
            mMediaPlayer = null
        }
    }

    // FROM
}