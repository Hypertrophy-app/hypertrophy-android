package app.hypertrophy.feature.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutTest {
    @Test
    fun defaultOrderMatchesEnumOrder() {
        val layout = DashboardLayout()
        assertEquals(TodayCard.entries.toList(), layout.order)
        assertEquals(TodayCard.entries.toSet(), layout.visible)
    }

    @Test
    fun moveUpSwapsAdjacentCards() {
        val layout = DashboardLayout()
        val moved = layout.moveUp(TodayCard.SQUAD_LIVE_TICKER)
        assertEquals(TodayCard.SQUAD_LIVE_TICKER, moved.order[0])
        assertEquals(TodayCard.TODAYS_PLAN, moved.order[1])
    }

    @Test
    fun moveUpAtTopIsNoOp() {
        val layout = DashboardLayout()
        val moved = layout.moveUp(TodayCard.TODAYS_PLAN)
        assertEquals(layout, moved)
    }

    @Test
    fun moveDownSwapsAdjacentCards() {
        val layout = DashboardLayout()
        val moved = layout.moveDown(TodayCard.TODAYS_PLAN)
        assertEquals(TodayCard.SQUAD_LIVE_TICKER, moved.order[0])
        assertEquals(TodayCard.TODAYS_PLAN, moved.order[1])
    }

    @Test
    fun toggleHidesAndShowsCard() {
        val layout = DashboardLayout()
        val hidden = layout.toggle(TodayCard.ACTIVE_CHALLENGE)
        assertTrue(TodayCard.ACTIVE_CHALLENGE !in hidden.visible)
        val shown = hidden.toggle(TodayCard.ACTIVE_CHALLENGE)
        assertTrue(TodayCard.ACTIVE_CHALLENGE in shown.visible)
    }

    @Test
    fun visibleCardsFiltersHidden() {
        val layout = DashboardLayout().toggle(TodayCard.CONSISTENCY_STRIP)
        val visible = layout.visibleCards()
        assertTrue(TodayCard.CONSISTENCY_STRIP !in visible)
        assertEquals(4, visible.size)
    }

    @Test
    fun collapseAllCollapsesEveryVisibleCard() {
        val layout = DashboardLayout().collapseAll()
        assertEquals(DashboardLayout().visible, layout.collapsed)
        assertTrue(layout.isCollapsed(TodayCard.TODAYS_PLAN))
    }

    @Test
    fun toggleCollapsedOnlyAffectsThatCard() {
        val collapsed = DashboardLayout().collapseAll()
        val expanded = collapsed.toggleCollapsed(TodayCard.ACTIVE_CHALLENGE)
        assertTrue(!expanded.isCollapsed(TodayCard.ACTIVE_CHALLENGE))
        assertTrue(expanded.isCollapsed(TodayCard.TODAYS_PLAN))
    }

    @Test
    fun expandAllClearsCollapsedCards() {
        val expanded = DashboardLayout().collapseAll().expandAll()
        assertTrue(expanded.collapsed.isEmpty())
        assertEquals(DashboardLayout(), expanded)
    }
}
