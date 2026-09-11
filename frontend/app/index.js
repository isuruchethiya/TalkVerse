import { StyleSheet, Text, View, Pressable, ActivityIndicator } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { Image } from "expo-image";
import { router, useNavigation } from "expo-router";
import { useEffect, useState } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { resetNavigationTo } from "../utils/navReset";

export default function index() {
  const navigation = useNavigation();

  // Gate rendering until we know whether the user is already logged in.
  // Without this, the full "Get Started" UI renders immediately and THEN
  // gets redirected away once the async AsyncStorage check resolves —
  // which is the flash a returning logged-in user sees for a split second.
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    async function checkSession() {
      try {
        const userJson = await AsyncStorage.getItem("user");
        if (userJson != null) {
          // Full reset (not router.replace) so this screen is dropped from
          // history entirely — back button from Home can't return here.
          resetNavigationTo(navigation, "home");
          return; // stay in the loading state; we're navigating away
        }
      } catch (e) {
        console.log(e);
      }
      setCheckingSession(false); // not logged in -> show Get Started
    }
    checkSession();
  }, []);

  if (checkingSession) {
    return (
      <LinearGradient style={stylesheet.loadingView} colors={["#39868D", "#007260"]}>
        <StatusBar hidden={true} />
        <ActivityIndicator size="large" color="#FFFFFF" />
      </LinearGradient>
    );
  }

  return (
    <LinearGradient style={{ flex: 1 }} colors={["#39868D", "#007260"]}>
      <StatusBar hidden={true} />

      <View style={stylesheet.view1}>
        <View>
          <Image
            style={stylesheet.image1}
            source={require("../assets/image1.png")}
          />
          <Image
            style={stylesheet.image2}
            source={require("../assets/image2.png")}
          />
          <Image
            style={stylesheet.image3}
            source={require("../assets/image3.png")}
          />
          <Image
            style={stylesheet.image4}
            source={require("../assets/talkVerse(2).png")}
          />

          <View style={stylesheet.view2}>
            <Text style={stylesheet.text1}>Let's Get,, </Text>
            <Text style={stylesheet.text2}>Started ...</Text>

            <View style={stylesheet.view3}>
              <Text style={stylesheet.text3}>
                "Connect With each other with chatting
              </Text>
              <Text style={stylesheet.text4}>
                Calling, Enjoy safe and private texting"
              </Text>
            </View>

            <Pressable
              style={stylesheet.pressable1}
              onPress={() => router.push("/signup")}
            >
              <Text style={stylesheet.text5}>Get Started</Text>
            </Pressable>

            <Pressable
              style={stylesheet.pressable2}
              onPress={() => router.push("/signin")}
            >
              <Text style={stylesheet.text4}>
                Already have an account? Sign In
              </Text>
            </Pressable>
          </View>
        </View>
      </View>
    </LinearGradient>
  );
}

const stylesheet = StyleSheet.create({
  loadingView: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  view1: {
    flex: 1,
  },
  image1: {
    height: 100,
    width: 100,
    borderRadius: 20,
    position: "absolute",
    top: 10,
    transform: [{ translateX: 20 }, { translateY: 50 }, { rotate: "-15deg" }],
  },
  image2: {
    height: 100,
    width: 100,
    borderRadius: 20,
    position: "absolute",
    top: -30,
    left: 100,
    transform: [{ translateX: 50 }, { translateY: 50 }, { rotate: "-5deg" }],
  },
  image3: {
    height: 100,
    width: 100,
    borderRadius: 20,
    position: "absolute",
    top: 130,
    left: -50,
    transform: [{ translateX: 50 }, { translateY: 50 }, { rotate: "15deg" }],
  },
  image4: {
    height: 200,
    width: 200,
    borderRadius: 20,
    position: "absolute",
    top: 110,
    left: 100,
    transform: [{ translateX: 50 }, { translateY: 50 }, { rotate: "-15deg" }],
  },
  view2: {
    paddingHorizontal: 22,
    position: "absolute",
    top: 400,
    width: "100%",
  },
  text1: {
    fontSize: 50,
    fontWeight: "bold",
    color: "white",
  },
  text2: {
    fontSize: 40,
    color: "white",
  },
  view3: {
    marginVertical: 22,
  },
  text3: {
    fontSize: 16,
    color: "white",
    marginVertical: 4,
  },
  text4: {
    fontSize: 16,
    color: "white",
  },
  pressable1: {
    height: 50,
    backgroundColor: "#FEFF9F",
    justifyContent: "center",
    alignItems: "center",
    borderRadius: 10,
    marginTop: 10,
    flexDirection: "row",
    columnGap: 10,
  },
  text5: {
    fontSize: 20,
    color: "green",
  },
  pressable2: {
    height: 25,
    justifyContent: "center",
    alignItems: "center",
    borderRadius: 10,
    marginTop: 10,
  },
});