package com.example.androidpos.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.androidpos.R;
import com.example.androidpos.ui.dashboard.MainActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MainActivityTest {
    @Rule public final ActivityScenarioRule<MainActivity> activity =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test public void exposesMainNavigation() {
        onView(withId(R.id.nav_products)).check(matches(isDisplayed())).perform(click());
        onView(withId(R.id.nav_order)).check(matches(isDisplayed())).perform(click());
        onView(withId(R.id.nav_history)).check(matches(isDisplayed())).perform(click());
        onView(withId(R.id.nav_statistics)).check(matches(isDisplayed())).perform(click());
        onView(withId(R.id.nav_home)).check(matches(isDisplayed())).perform(click());
        onView(withText("Thu ngân POS")).check(matches(isDisplayed()));
    }
}
