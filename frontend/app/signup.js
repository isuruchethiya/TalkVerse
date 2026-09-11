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
import * as ImagePicker from "expo-image-picker";
import { FontAwesome6 } from "@expo/vector-icons";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { buildSignUpUrl } from "../constants/api";
import { resetNavigationTo } from "../utils/navReset";

export default function signup() {
  const navigation = useNavigation();

  const [getMobile, setMobile] = useState("");
  const [getFirstName, setFirstName] = useState("");
  const [getLastName, setLastName] = useState("");
  const [getPassword, setPassword] = useState("");
  const [getImage, setImage] = useState(null);

  const logoPath = require("../assets/talkVerse(2).png");
  const [isPasswordShown, setIsPasswordShown] = useState(false);

  // Gate rendering until we know whether the user is already logged in —
  // avoids a flash of the Sign Up form before the redirect kicks in.
  const [checkingSession, setCheckingSession] = useState(true);

  // If user already logged in, skip straight to home
  useEffect(() => {
    async function checkSession() {
      try {
        const userJson = await AsyncStorage.getItem("user");
        if (userJson != null) {
          // Full reset, not router.replace — drops Get Started + Sign Up
          // from history so the back button from Home can't return here.
          resetNavigationTo(navigation, "home");
          return;
        }
      } catch (e) {
        console.log(e);
      }
      setCheckingSession(false);
    }
    checkSession();
  }, []);

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
            <Text style={stylesheet.text1}>Create an Account</Text>
            <Text style={stylesheet.text2}>
              Hello! Welcome to TalkVerse. Connect With your friend today
            </Text>
          </View>

          {/* ── Avatar picker ── */}
          <Pressable
            onPress={async () => {
              let result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                aspect: [1, 1],
                quality: 0.8,
              });
              if (!result.canceled) {
                setImage(result.assets[0].uri);
              }
            }}
            style={stylesheet.avatarWrap}
          >
            {getImage ? (
              <Image
                source={getImage}
                style={stylesheet.avatarCircle}
                contentFit="cover"
              />
            ) : (
              <View style={stylesheet.avatarCircle}>
                <FontAwesome6 name="camera" size={28} color="#387478" />
                <Text style={stylesheet.avatarHint}>Add Photo</Text>
              </View>
            )}
          </Pressable>

          <View style={stylesheet.view2}>
            <Text style={stylesheet.text3}>Mobile Number</Text>
            <TextInput
              style={stylesheet.input1}
              placeholder="Enter your Mobile No"
              inputMode={"tel"}
              maxLength={10}
              onChangeText={(text) => setMobile(text)}
            />

            <Text style={stylesheet.text3}>First Name</Text>
            <TextInput
              style={stylesheet.input1}
              placeholder="Enter your First Name"
              inputMode={"text"}
              onChangeText={(text) => setFirstName(text)}
            />

            <Text style={stylesheet.text3}>Last Name</Text>
            <TextInput
              style={stylesheet.input1}
              placeholder="Enter your Last Name"
              inputMode={"text"}
              onChangeText={(text) => setLastName(text)}
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

            <Pressable
              style={stylesheet.pressable1}
              onPress={async () => {
                let formData = new FormData();
                formData.append("mobile", getMobile);
                formData.append("firstName", getFirstName);
                formData.append("lastName", getLastName);
                formData.append("password", getPassword);

                if (getImage != null) {
                  formData.append("avatarImage", {
                    name: "avatar.png",
                    type: "image/png",
                    uri: getImage,
                  });
                }

                let response = await fetch(buildSignUpUrl(), {
                  method: "POST",
                  body: formData,
                });

                if (response.ok) {
                  let json = await response.json();
                  if (json.success) {
                    // Registration done → go to signin (not index, and not
                    // logged in yet so no full reset needed here — a
                    // normal replace is fine, they still need to sign in).
                    Alert.alert(
                      "Success",
                      "Account created! Please sign in.",
                      [{ text: "OK", onPress: () => router.replace("/signin") }]
                    );
                  } else {
                    Alert.alert("Error", json.message);
                  }
                }
              }}
            >
              <Text style={stylesheet.text5}>Sign Up</Text>
            </Pressable>

            <View style={stylesheet.view5}>
              <View style={stylesheet.view6} />
              <Text style={stylesheet.text6}>Or Sign Up with</Text>
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

          {/* ── "Already have an account" — push to signin, don't replace ── */}
          <View style={stylesheet.view8}>
            <Text style={{ fontSize: 16, color: "black" }}>
              Already have an account?{" "}
            </Text>
            <Pressable onPress={() => router.push("/signin")}>
              <Text style={stylesheet.text4}>Sign In</Text>
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
  avatarWrap: {
    alignItems: "center",
    marginBottom: 16,
  },
  avatarCircle: {
    width: 90,
    height: 90,
    borderRadius: 45,
    borderWidth: 2,
    borderColor: "#387478",
    backgroundColor: "#EAF4F4",
    justifyContent: "center",
    alignItems: "center",
    overflow: "hidden",
  },
  avatarHint: {
    fontSize: 11,
    color: "#387478",
    marginTop: 4,
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
    borderWidth: 2,
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
});