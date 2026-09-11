import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Image,
} from "react-native";
import { FontAwesome6 } from "@expo/vector-icons"; 
import { useRouter } from "expo-router";
import { DEFAULT_AVATAR_URL } from "../constants/api"; // Use this hook instead of router directly

export default function ProfileUpdateScreen() {
  const [mobileNumber, setMobileNumber] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [password, setPassword] = useState("");

  const router = useRouter(); // Get the router instance

  return (
    <View style={styles.container}>
      {/* Back Button */}
      <TouchableOpacity 
        style={styles.backButton} 
        onPress={() => {
          router.push("/userprofile"); // Navigate to User Profile
        }}
      >
        <FontAwesome6 name="chevron-left" size={24} color="black" />
      </TouchableOpacity>

      {/* Profile Picture Section */}
      <View style={styles.profilePictureContainer}>
        <Image
          style={styles.profilePicture}
          source={{ uri: DEFAULT_AVATAR_URL }}
        />
        <TouchableOpacity style={styles.editIcon}>
          <FontAwesome6 name="edit" size={20} color="white" />
        </TouchableOpacity>
      </View>
      <Text style={styles.updateProfileText}>Update Your Profile Picture</Text>

      {/* Input Fields */}
      <View style={styles.inputContainer}>
        <Text style={styles.label}>Mobile Number</Text>
        <TextInput
          style={styles.input}
          placeholder="Enter your Mobile number"
          value={mobileNumber}
          onChangeText={setMobileNumber}
        />

        <Text style={styles.label}>First Name</Text>
        <TextInput
          style={styles.input}
          placeholder="Enter your First Name"
          value={firstName}
          onChangeText={setFirstName}
        />

        <Text style={styles.label}>Last Name</Text>
        <TextInput
          style={styles.input}
          placeholder="Enter your Last Name"
          value={lastName}
          onChangeText={setLastName}
        />

        <Text style={styles.label}>Password</Text>
        <TextInput
          style={styles.input}
          placeholder="Enter your Password"
          secureTextEntry
          value={password}
          onChangeText={setPassword}
        />
      </View>

      {/* Update Profile Button */}
      <TouchableOpacity style={styles.updateButton}>
        <Text style={styles.updateButtonText}>Update Your Profile</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
    paddingHorizontal: 20,
    paddingTop: 50,
  },
  backButton: {
    position: "absolute",
    top: 40,
    left: 20,
  },
  profilePictureContainer: {
    alignSelf: "center",
    marginTop: 20,
    position: "relative",
  },
  profilePicture: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: "#e0e0e0",
  },
  editIcon: {
    position: "absolute",
    bottom: 0,
    right: 0,
    backgroundColor: "#00e676",
    borderRadius: 15,
    padding: 5,
  },
  updateProfileText: {
    textAlign: "center",
    marginTop: 10,
    fontSize: 16,
    color: "#6e6e6e",
  },
  inputContainer: {
    marginTop: 20,
  },
  label: {
    fontSize: 14,
    marginBottom: 5,
    color: "#6e6e6e",
  },
  input: {
    backgroundColor: "#d0d0d0",
    padding: 10,
    borderRadius: 10,
    marginBottom: 20,
    fontSize: 16,
  },
  updateButton: {
    backgroundColor: "#42a5f5",
    paddingVertical: 15,
    borderRadius: 10,
    alignItems: "center",
    marginTop: 20,
  },
  updateButtonText: {
    color: "white",
    fontSize: 16,
    fontWeight: "bold",
  },
});
