import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

admin.initializeApp();

// Haversine formula — calculeaza distanta in metri
function getDistanceMeters(
  lat1: number, lng1: number,
  lat2: number, lng2: number
): number {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
    Math.sin(dLng / 2) * Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export const onFallDetected = onDocumentCreated(
  "fall_events/{eventId}",
  async (event) => {
    const fallData = event.data?.data();
    if (!fallData) return;

    const fromUid = fallData.uid;
    const fromName = fallData.name;

    const userDoc = await admin.firestore().collection("users").doc(fromUid).get();
    const userData = userDoc.data();
    const fallLat = userData?.lastLatitude ?? fallData.latitude;
    const fallLng = userData?.lastLongitude ?? fallData.longitude;

    // --- 1. Send SMS to emergency contacts (always, no distance check) ---
    const emergencyContacts = userData?.emergencyContacts as Array<{name: string, phone: string}> | undefined;

    if (emergencyContacts && emergencyContacts.length > 0) {
      const twilio = require("twilio")(
        process.env.TWILIO_SID,
        process.env.TWILIO_TOKEN
      );

      const mapsLink = `https://maps.google.com/?q=${fallLat},${fallLng}`;
      const smsBody = `ALERTA: ${fromName} a cazut si are nevoie de ajutor! Locatie: ${mapsLink}`;

      const smsPromises = emergencyContacts.map((contact) => {
        return twilio.messages.create({
          body: smsBody,
          from: process.env.TWILIO_PHONE,
          to: contact.phone,
        }).then(() => {
          console.log(`SMS sent to ${contact.name} (${contact.phone})`);
        }).catch((err: any) => {
          console.error(`SMS failed to ${contact.phone}: ${err.message}`);
        });
      });

      await Promise.all(smsPromises);
    } else {
      console.log("No emergency contacts configured");
    }

    // --- 2. Send push notifications to nearby users (existing code, untouched) ---
    const RADIUS_METERS = 500;

    const usersSnapshot = await admin.firestore().collection("users").get();
    const tokens: string[] = [];

    usersSnapshot.forEach((doc) => {
      const user = doc.data();
      if (user.uid === fromUid || !user.fcmToken) return;

      const userLat = user.lastLatitude;
      const userLng = user.lastLongitude;

      // Skip users without location
      if (userLat == null || userLng == null) return;

      const distance = getDistanceMeters(fallLat, fallLng, userLat, userLng);
      console.log(`User ${user.name}: ${distance.toFixed(0)}m away`);

      if (distance <= RADIUS_METERS) {
        tokens.push(user.fcmToken);
      }
    });

    if (tokens.length === 0) {
      console.log("No users within 500m to notify");
      return;
    }

    console.log(`Notifying ${tokens.length} users within ${RADIUS_METERS}m`);

    const message = {
      data: {
        fromUid: fromUid,
        fromName: fromName,
        latitude: fallLat.toString(),
        longitude: fallLng.toString(),
        type: "fall_alert",
      },
      android: {
        priority: "high" as const,
      },
      tokens: tokens,
    };

    const response = await admin.messaging().sendEachForMulticast(message);
    console.log(`Sent ${response.successCount} notifications, ${response.failureCount} failed`);

    response.responses.forEach((resp, idx) => {
      if (!resp.success) {
        console.error(`Failed token ${idx}: ${resp.error?.code} - ${resp.error?.message}`);
      }
    });
  }
);
