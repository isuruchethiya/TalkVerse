import {
  Alert,
  SafeAreaView,
  View,
  StyleSheet,
  Text,
  Pressable,
  TextInput,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
} from "react-native";
import { router, useNavigation } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { Image } from "expo-image";
import { FontAwesome6 } from "@expo/vector-icons";
import Checkbox from "expo-checkbox";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { buildGetLettersUrl, buildSignInUrl, buildAvatarUrl } from "../constants/api";
import { resetNavigationTo } from "../utils/navReset";

export default function signin() {
  const navigation = useNavigation();

  const [getMobile, setMobile] = useState("");
  const [getPassword, setPassword] = useState("");
  const [getLetters, setLetters] = useState("");         // e.g. "IS"
  const [avatarFound, setAvatarFound] = useState(false); // true = show image

  // Gate rendering until we know whether the user is already logged in —
  // avoids a flash of the Sign In form before the redirect kicks in.
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    async function checkUserInAsyncStorage() {
      try {
        let userJson = await AsyncStorage.getItem("user");
        if (userJson != null) {
          // Full reset, not router.replace — drops Get Started + Sign In
          // from history so the back button from Home can't return here.
          resetNavigationTo(navigation, "home");
          return;
        }
      } catch (e) {
        console.log(e);
      }
      setCheckingSession(false);
    }
    checkUserInAsyncStorage();
  }, []);

  const logoPath = require("../assets/talkVerse(2).png");
  const [isPasswordShown, setIsPasswordShown] = useState(false);
  const [isChecked, setIsCheked] = useState(false);

  if (checkingSession) {
    return (
      <SafeAreaView style={stylesheet.loadingView}>
        <StatusBar hidden={true} />
        <ActivityIndicator size="large" color="#387478" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: "white" }}>
      <StatusBar hidden={true} />
      <ScrollView>
        <View style={stylesheet.view1}>
          <View style={{ marginVertical: 22 }}>
            <Image
              source={logoPath}
              style={stylesheet.image1}
              contentFit={"contain"}
            />
            <Text style={stylesheet.text1}>Sign In</Text>
            <Text style={stylesheet.text2}>
              Hello! Welcome to TalkVerse. Connect With your friend today
            </Text>
          </View>

          {/* ── Avatar: show only when mobile number typed (10 digits) ── */}
          {(getLetters.length > 0 || avatarFound) && (
            <View style={stylesheet.avatarWrap}>
              {avatarFound ? (
                // Profile image from server
                <Image
                  source={{ uri: buildAvatarUrl(getMobile) }}
                  style={stylesheet.avatarCircle}
                  contentFit="cover"
                />
              ) : (
                // Initials circle (e.g. "IS" for Isuru Sunil)
                <View style={stylesheet.avatarCircle}>
                  <Text style={stylesheet.text7}>{getLetters}</Text>
                </View>
              )}
            </View>
          )}

          <View style={stylesheet.view2}>
            <Text style={stylesheet.text3}>Mobile Number</Text>
            <TextInput
              style={stylesheet.input1}
              placeholder="Enter your Mobile No"
              inputMode={"tel"}
              maxLength={10}
              onChangeText={(text) => setMobile(text)}
              onEndEditing={async () => {
                if (getMobile.length === 10) {
                  try {
                    let response = await fetch(buildGetLettersUrl(getMobile));
                    if (response.ok) {
                      let json = await response.json();
                      setLetters(json.letters || "");
                      setAvatarFound(json.avatar_image_found === true);
                    }
                  } catch (e) {
                    console.log(e);
                  }
                } else {
                  // Reset if mobile cleared
                  setLetters("");
                  setAvatarFound(false);
                }
              }}
            />

            <View style={{ marginBottom: 12 }}>
              <Text style={stylesheet.text3}>Password</Text>
              <View style={stylesheet.view3}>
                <TextInput
                  style={stylesheet.input3}
                  placeholder="Enter your password"
                  secureTextEntry={isPasswordShown}
                  onChangeText={(text) => setPassword(text)}
                />
                <TouchableOpacity
                  style={stylesheet.touchableopacity1}
                  onPress={() => setIsPasswordShown(!isPasswordShown)}
                >
                  {isPasswordShown ? (
                    <FontAwesome6 name={"eye-slash"} color={"black"} size={20} />
                  ) : (
                    <FontAwesome6 name={"eye"} color={"black"} size={20} />
                  )}
                </TouchableOpacity>
              </View>
            </View>

            <View style={stylesheet.view4}>
              <Checkbox
                style={stylesheet.checkbox1}
                value={isChecked}
                onValueChange={setIsCheked}
                color={isChecked ? "#FF0000" : undefined}
              />
              <Text>I agree to the terms and conditions</Text>
            </View>

            <Pressable
              style={stylesheet.pressable1}
              onPress={async () => {
                let response = await fetch(buildSignInUrl(), {
                  method: "POST",
                  body: JSON.stringify({
                    mobile: getMobile,
                    password: getPassword,
                  }),
                  headers: { "Content-Type": "application/json" },
                });

                if (response.ok) {
                  let json = await response.json();
                  if (json.success) {
                    try {
                      await AsyncStorage.setItem("user", JSON.stringify(json.user));
                      // Full reset, not router.replace — Get Started + Sign
                      // In are dropped from history entirely.
                      resetNavigationTo(navigation, "home");
                    } catch (e) {
                      Alert.alert("Error", "Unable to process your request");
                    }
                  } else {
                    Alert.alert("Error", json.message);
                  }
                }
              }}
            >
              <Text style={stylesheet.text5}>Sign In</Text>
            </Pressable>

            <View style={stylesheet.view5}>
              <View style={stylesheet.view6} />
              <Text style={stylesheet.text6}>Or Sign In with</Text>
              <View style={stylesheet.view6} />
            </View>
          </View>

          <View style={stylesheet.view7}>
            <TouchableOpacity
              onPress={() => console.log("pressed")}
              style={stylesheet.touchableopacity2}
            >
              <Image
                source={require("../assets/facebook.png")}
                style={stylesheet.facebook}
                resizeMod="contain"
              />
              <Text>Facebook</Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => console.log("pressed")}
              style={stylesheet.touchableopacity2}
            >
              <Image
                source={require("../assets/googal.png")}
                style={stylesheet.facebook}
                resizeMod="contain"
              />
              <Text>Google</Text>
            </TouchableOpacity>
          </View>

          <View style={stylesheet.view8}>
            <Text style={{ fontSize: 16, color: "black" }}>
              Don't Have an account?{" "}
            </Text>
            <Pressable
              onPress={() => router.push("/signup")}
            >
              <Text style={stylesheet.text4}>Create Account</Text>
            </Pressable>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const stylesheet = StyleSheet.create({
  loadingView: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "white",
  },
  view1: {
    flex: 1,
    marginHorizontal: 22,
  },
  image1: {
    width: "100%",
    height: 90,
  },
  text1: {
    fontSize: 22,
    fontWeight: "bold",
    marginVertical: 12,
    color: "#257180",
  },
  text2: {
    fontSize: 16,
    color: "#257180",
  },
  // Outer wrapper just for centering
  avatarWrap: {
    alignItems: "center",
    marginBottom: 12,
  },
  // Single circle used for both letter-avatar and image-avatar
  avatarCircle: {
    width: 90,
    height: 90,
    borderRadius: 45,
    borderWidth: 1,
    borderColor: "#ccc",
    backgroundColor: "#EAF4F4",
    justifyContent: "center",
    alignItems: "center",
    overflow: "hidden",
  },
  view2: {
    marginBottom: 12,
  },
  text3: {
    fontSize: 16,
    fontWeight: "400",
    marginVertical: 8,
  },
  input1: {
    width: "100%",
    height: 48,
    borderWidth: 1,
    borderRadius: 8,
    paddingStart: 10,
    fontSize: 18,
    borderColor: "#384B70",
  },
  touchableopacity1: {
    position: "absolute",
    right: 12,
  },
  view3: {
    width: "100%",
    height: 48,
    borderColor: "black",
    borderWidth: 1,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    paddingLeft: 22,
  },
  input3: {
    width: "100%",
    fontSize: 18,
  },
  view4: {
    flexDirection: "row",
    marginVertical: 6,
  },
  checkbox1: {
    marginRight: 8,
  },
  pressable1: {
    height: 50,
    backgroundColor: "#387478",
    justifyContent: "center",
    alignItems: "center",
    borderRadius: 10,
    marginTop: 10,
    flexDirection: "row",
    columnGap: 10,
  },
  text5: {
    fontSize: 20,
    color: "#F4F6FF",
  },
  view5: {
    flexDirection: "row",
    alignItems: "center",
    marginVertical: 20,
  },
  view6: {
    flex: 1,
    height: 1,
    backgroundColor: "#B7B7B7",
    marginHorizontal: 10,
  },
  text6: {
    fontSize: 14,
  },
  view7: {
    flexDirection: "row",
    justifyContent: "center",
  },
  touchableopacity2: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    height: 52,
    borderWidth: 1,
    borderColor: "#B7B7B7",
    marginRight: 4,
    borderRadius: 10,
  },
  facebook: {
    height: 36,
    width: 36,
    marginRight: 8,
  },
  view8: {
    flexDirection: "row",
    justifyContent: "center",
    marginVertical: 22,
  },
  text4: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#15B392",
    marginLeft: 6,
  },
  text7: {
    fontSize: 35,
    fontWeight: "bold",
    color: "#384B70",
  },
});