import { CommonActions } from "@react-navigation/native";

/**
 * Fully replaces the navigation history with a single screen.
 *
 * router.replace("/home") (expo-router) only swaps out the CURRENT screen —
 * anything pushed onto the stack before it (e.g. the "Get Started" index
 * screen, or Sign In) stays underneath and is still reachable with the
 * Android hardware back button / iOS swipe-back gesture. That's why an
 * already-logged-in user could still land back on Get Started after
 * pressing back from Home.
 *
 * This does a proper stack reset instead — the target screen becomes the
 * ONLY entry in history, so there's nothing left to go "back" to.
 *
 * Usage:
 *   const navigation = useNavigation(); // from "expo-router"
 *   resetNavigationTo(navigation, "home");        // after login
 *   resetNavigationTo(navigation, "index");       // after logout
 */
export function resetNavigationTo(navigation, routeName, params) {
  navigation.dispatch(
    CommonActions.reset({
      index: 0,
      routes: [{ name: routeName, params }],
    })
  );
}