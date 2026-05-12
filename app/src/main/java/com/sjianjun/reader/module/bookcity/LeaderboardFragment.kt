package com.sjianjun.reader.module.bookcity

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sjianjun.reader.R
import com.sjianjun.reader.databinding.FragmentLeaderboardBinding
import com.sjianjun.reader.preferences.globalConfig

class LeaderboardFragment : Fragment() {
    private var binding: FragmentLeaderboardBinding? = null

    private val onlineFragment by lazy { OnlineLeaderboardFragment.newInstance() }
    private val recommendationFragment by lazy { RecommendationLeaderboardFragment.newInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        binding = FragmentLeaderboardBinding.bind(view)
        showTab(currentPosition())
        updateToolbarTitle()
    }

    override fun onResume() {
        super.onResume()
        updateToolbarTitle()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.leaderboard_toolbar_menu, menu)
        updateMenuTitleState(menu)
        updateToolbarTitle()
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        updateMenuTitleState(menu)
        updateToolbarTitle()
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_leaderboard_online -> {
                if (currentPosition() != 0) {
                    globalConfig.leaderboardTab = "online"
                    showTab(0)
                    activity?.invalidateOptionsMenu()
                    updateToolbarTitle()
                }
                true
            }

            R.id.action_leaderboard_recommend -> {
                if (currentPosition() != 1) {
                    globalConfig.leaderboardTab = "recommend"
                    showTab(1)
                    activity?.invalidateOptionsMenu()
                    updateToolbarTitle()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showTab(position: Int) {
        val fragment = if (position == 1) recommendationFragment else onlineFragment
        childFragmentManager.beginTransaction()
            .replace(R.id.leaderboard_fragment_container, fragment)
            .commit()
    }

    private fun updateMenuTitleState(menu: Menu) {
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.mdr_white)
        val unselectedColor = ContextCompat.getColor(requireContext(), R.color.mdr_grey_500)
        val recommendSelected = currentPosition() == 1

        menu.findItem(R.id.action_leaderboard_online)?.apply {
            title = coloredTitle("在线榜", if (recommendSelected) unselectedColor else selectedColor)
        }

        menu.findItem(R.id.action_leaderboard_recommend)?.apply {
            title = coloredTitle("推荐榜", if (recommendSelected) selectedColor else unselectedColor)
        }
    }

    private fun coloredTitle(text: String, color: Int): CharSequence {
        val span = SpannableString(text)
        span.setSpan(ForegroundColorSpan(color), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    private fun updateToolbarTitle() {
        val title = if (currentPosition() == 1) "书友推荐榜" else "书友在线榜"
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    private fun currentPosition(): Int = if (globalConfig.leaderboardTab == "recommend") 1 else 0

    override fun onDestroyView() {
        super.onDestroyView()
        setHasOptionsMenu(false)
        binding = null
    }
}
