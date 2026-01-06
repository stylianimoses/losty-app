/**
 * Firebase Cloud Functions for Lost and Found App
 * Implements Fuzzy Matching Algorithm with FCM Notifications
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp();

/**
 * Helper Function: Calculate distance between two coordinates using Haversine Formula
 * @param {number} lat1 - Latitude of first point
 * @param {number} lon1 - Longitude of first point
 * @param {number} lat2 - Latitude of second point
 * @param {number} lon2 - Longitude of second point
 * @returns {number} Distance in kilometers
 */
function getDistanceInKm(lat1, lon1, lat2, lon2) {
    const R = 6371; // Earth's radius in kilometers
    const dLat = toRadians(lat2 - lat1);
    const dLon = toRadians(lon2 - lon1);
    
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const distance = R * c;
    
    return distance;
}

/**
 * Convert degrees to radians
 */
function toRadians(degrees) {
    return degrees * (Math.PI / 180);
}

/**
 * Helper Function: Calculate Dice's Coefficient for string similarity
 * @param {string} str1 - First string
 * @param {string} str2 - Second string
 * @returns {number} Similarity score between 0 and 1
 */
function diceCoefficient(str1, str2) {
    if (str1 === str2) return 1.0;
    if (str1.length < 2 || str2.length < 2) return 0.0;
    
    // Convert to lowercase and remove extra whitespace
    const s1 = str1.toLowerCase().trim();
    const s2 = str2.toLowerCase().trim();
    
    // Create bigrams (pairs of adjacent characters)
    const pairs1 = new Map();
    for (let i = 0; i < s1.length - 1; i++) {
        const pair = s1.substring(i, i + 2);
        pairs1.set(pair, (pairs1.get(pair) || 0) + 1);
    }
    
    const pairs2 = new Map();
    for (let i = 0; i < s2.length - 1; i++) {
        const pair = s2.substring(i, i + 2);
        pairs2.set(pair, (pairs2.get(pair) || 0) + 1);
    }
    
    // Count matches
    let matches = 0;
    const allPairs = new Set([...pairs1.keys(), ...pairs2.keys()]);
    
    for (const pair of allPairs) {
        const count1 = pairs1.get(pair) || 0;
        const count2 = pairs2.get(pair) || 0;
        matches += Math.min(count1, count2);
    }
    
    // Dice's Coefficient = 2 * matches / (total pairs in both strings)
    const totalPairs = (s1.length - 1) + (s2.length - 1);
    return totalPairs === 0 ? 0.0 : (2 * matches) / totalPairs;
}

/**
 * Helper Function: Calculate Euclidean distance between two RGB color arrays
 * @param {Array<number>} rgb1 - First RGB array [R, G, B]
 * @param {Array<number>} rgb2 - Second RGB array [R, G, B]
 * @returns {number} Normalized color similarity score between 0 and 1
 */
function getColorScore(rgb1, rgb2) {
    if (!rgb1 || !rgb2 || rgb1.length !== 3 || rgb2.length !== 3) {
        return 0.0;
    }
    
    // Calculate Euclidean distance
    const distance = Math.sqrt(
        Math.pow(rgb1[0] - rgb2[0], 2) +
        Math.pow(rgb1[1] - rgb2[1], 2) +
        Math.pow(rgb1[2] - rgb2[2], 2)
    );
    
    // Normalize to 0-1 scale (max distance is sqrt(3 * 255^2) = ~441.67)
    const maxDistance = Math.sqrt(3 * Math.pow(255, 2));
    const normalizedDistance = distance / maxDistance;
    
    // Convert distance to similarity (closer = higher score)
    return 1.0 - normalizedDistance;
}

/**
 * Helper Function: Calculate time-based similarity score
 * @param {number} timestamp1 - First timestamp (milliseconds)
 * @param {number} timestamp2 - Second timestamp (milliseconds)
 * @returns {number} Time similarity score between 0 and 1
 */
function getTimeScore(timestamp1, timestamp2) {
    if (!timestamp1 || !timestamp2) return 0.0;
    
    const timeDiff = Math.abs(timestamp1 - timestamp2);
    
    // Convert to days
    const daysDiff = timeDiff / (1000 * 60 * 60 * 24);
    
    // Score decreases as time difference increases
    // Items reported within 1 day get score of 1.0
    // Items reported within 30 days get score of 0.5
    // Items reported within 90 days get score of 0.1
    // Items reported more than 90 days apart get score approaching 0
    if (daysDiff <= 1) return 1.0;
    if (daysDiff <= 7) return 1.0 - (daysDiff - 1) / 6 * 0.3; // 1.0 to 0.7
    if (daysDiff <= 30) return 0.7 - (daysDiff - 7) / 23 * 0.4; // 0.7 to 0.3
    if (daysDiff <= 90) return 0.3 - (daysDiff - 30) / 60 * 0.2; // 0.3 to 0.1
    return Math.max(0.0, 0.1 - (daysDiff - 90) / 365 * 0.1); // 0.1 to 0.0
}

/**
 * Helper Function: Calculate geographic similarity score
 * @param {number} lat1 - Latitude of first point
 * @param {number} lon1 - Longitude of first point
 * @param {number} lat2 - Latitude of second point
 * @param {number} lon2 - Longitude of second point
 * @returns {number} Geographic similarity score between 0 and 1
 */
function getGeographicScore(lat1, lon1, lat2, lon2) {
    if (!lat1 || !lon1 || !lat2 || !lon2) return 0.0;
    
    const distanceKm = getDistanceInKm(lat1, lon1, lat2, lon2);
    
    // Score decreases as distance increases
    // Items within 1 km get score of 1.0
    // Items within 5 km get score of 0.8
    // Items within 10 km get score of 0.6
    // Items within 25 km get score of 0.4
    // Items within 50 km get score of 0.2
    // Items more than 50 km apart get score approaching 0
    if (distanceKm <= 1) return 1.0;
    if (distanceKm <= 5) return 1.0 - (distanceKm - 1) / 4 * 0.2; // 1.0 to 0.8
    if (distanceKm <= 10) return 0.8 - (distanceKm - 5) / 5 * 0.2; // 0.8 to 0.6
    if (distanceKm <= 25) return 0.6 - (distanceKm - 10) / 15 * 0.2; // 0.6 to 0.4
    if (distanceKm <= 50) return 0.4 - (distanceKm - 25) / 25 * 0.2; // 0.4 to 0.2
    return Math.max(0.0, 0.2 - (distanceKm - 50) / 100 * 0.2); // 0.2 to 0.0
}

/**
 * Helper Function: Send FCM notification to the owner
 * @param {string} ownerId - User ID of the owner who should receive the notification
 * @param {string} lostReportId - Document ID of the lost report
 * @param {string} foundReportId - Document ID of the found report
 * @returns {Promise<void>}
 */
async function sendMatchNotification(ownerId, lostReportId, foundReportId) {
    try {
        // Try Realtime Database first for fcmToken
        let fcmToken = null;
        try {
            const dbSnap = await admin.database().ref(`Users/${ownerId}/fcmToken`).get();
            if (dbSnap && dbSnap.exists()) {
                fcmToken = dbSnap.val();
            }
        } catch (dbErr) {
            console.error(`Error reading fcmToken from Realtime DB for ${ownerId}:`, dbErr);
        }

        // Fallback to Firestore if not found in Realtime DB
        if (!fcmToken) {
            const userDoc = await admin.firestore()
                .collection('users')
                .doc(ownerId)
                .get();

            if (userDoc.exists) {
                fcmToken = userDoc.data()?.fcmToken;
            }
        }

        if (!fcmToken) {
            console.error(`FCM token not found for user: ${ownerId}`);
            return;
        }

        // Prepare the notification message
        const message = {
            notification: {
                title: 'Possible Match Found!',
                body: 'A potential match has been found for your lost item. Check the app for details.',
            },
            data: {
                type: 'match_found',
                lostReportId: lostReportId,
                foundReportId: foundReportId,
                click_action: 'FLUTTER_NOTIFICATION_CLICK',
            },
            token: fcmToken,
        };

        // Send the notification
        const response = await admin.messaging().send(message);
        console.log(`Successfully sent notification to ${ownerId}:`, response);
    } catch (error) {
        console.error(`Error sending notification to ${ownerId}:`, error);
        throw error;
    }
}

/**
 * Main Cloud Function: Triggered when a new report is created
 * Executes fuzzy matching algorithm and sends FCM notification if match score >= 0.85
 */
exports.onReportCreate = functions.firestore
    .document('reports/{reportId}')
    .onCreate(async (snap, context) => {
        const newReport = snap.data();
        const newReportId = context.params.reportId;
        
        console.log(`Processing new report: ${newReportId}, type: ${newReport.type}`);
        
        // Validate required fields
        if (!newReport.type || !newReport.userId) {
            console.error('Missing required fields: type or userId');
            return null;
        }
        
        // Skip if report already has a match
        if (newReport.matchId) {
            console.log(`Report ${newReportId} already has a match, skipping`);
            return null;
        }
        
        // Determine opposite type
        const oppositeType = newReport.type === 'LOST' ? 'FOUND' : 'LOST';
        
        try {
            // Query opposite reports that don't have a match yet
            // Note: Firestore supports querying for null, but we also filter in code for safety
            const oppositeReportsSnapshot = await admin.firestore()
                .collection('reports')
                .where('type', '==', oppositeType)
                .get();
            
            console.log(`Found ${oppositeReportsSnapshot.size} potential matches to check`);
            
            let bestMatch = null;
            let bestScore = 0;
            
            // Loop through all opposite reports and calculate match scores
            for (const doc of oppositeReportsSnapshot.docs) {
                const candidateReport = doc.data();
                const candidateReportId = doc.id;
                
                // Skip if report already has a match (matchId is not null and not undefined)
                if (candidateReport.matchId) {
                    continue;
                }
                
                // Skip if it's the same user's report
                if (candidateReport.userId === newReport.userId) {
                    continue;
                }
                
                // Extract data for calculations
                const newLat = newReport.latitude;
                const newLon = newReport.longitude;
                const candidateLat = candidateReport.latitude;
                const candidateLon = candidateReport.longitude;
                
                const newDescription = newReport.description || '';
                const candidateDescription = candidateReport.description || '';
                
                const newColor = newReport.dominantColorRgb || [0, 0, 0];
                const candidateColor = candidateReport.dominantColorRgb || [0, 0, 0];
                
                const newTimestamp = newReport.timestamp || Date.now();
                const candidateTimestamp = candidateReport.timestamp || Date.now();
                
                // Calculate individual scores
                const geographicScore = getGeographicScore(
                    newLat, newLon, candidateLat, candidateLon
                );
                
                const descriptionScore = diceCoefficient(
                    newDescription, candidateDescription
                );
                
                const colorScore = getColorScore(newColor, candidateColor);
                
                const timeScore = getTimeScore(newTimestamp, candidateTimestamp);
                
                // Calculate final weighted score
                // Geographic: 35%, Description: 35%, Color: 25%, Time: 5%
                const finalScore = (
                    geographicScore * 0.35 +
                    descriptionScore * 0.35 +
                    colorScore * 0.25 +
                    timeScore * 0.05
                );
                
                console.log(`Match score for ${candidateReportId}: ${finalScore.toFixed(4)}`);
                console.log(`  Geographic: ${geographicScore.toFixed(4)}, Description: ${descriptionScore.toFixed(4)}, Color: ${colorScore.toFixed(4)}, Time: ${timeScore.toFixed(4)}`);
                
                // Track the best match
                if (finalScore > bestScore) {
                    bestScore = finalScore;
                    bestMatch = {
                        reportId: candidateReportId,
                        score: finalScore,
                        userId: candidateReport.userId
                    };
                }
            }
            
            // If best match score >= 0.85, link the reports and send notification
            if (bestMatch && bestScore >= 0.85) {
                console.log(`Match found! Score: ${bestScore.toFixed(4)}, Linking reports: ${newReportId} <-> ${bestMatch.reportId}`);
                
                const batch = admin.firestore().batch();
                
                // Update both reports with matchId
                const newReportRef = admin.firestore().collection('reports').doc(newReportId);
                const matchedReportRef = admin.firestore().collection('reports').doc(bestMatch.reportId);
                
                batch.update(newReportRef, { matchId: bestMatch.reportId });
                batch.update(matchedReportRef, { matchId: newReportId });
                
                await batch.commit();
                
                // Determine which user is the owner (the one who posted the LOST report)
                const ownerId = newReport.type === 'LOST' ? newReport.userId : bestMatch.userId;
                const lostReportId = newReport.type === 'LOST' ? newReportId : bestMatch.reportId;
                const foundReportId = newReport.type === 'FOUND' ? newReportId : bestMatch.reportId;
                
                // Send notification to the owner
                await sendMatchNotification(ownerId, lostReportId, foundReportId);
                
                console.log(`Successfully linked reports and sent notification to owner: ${ownerId}`);
            } else {
                console.log(`No match found above threshold. Best score: ${bestScore.toFixed(4)}`);
            }
            
            return null;
        } catch (error) {
            console.error('Error in onReportCreate:', error);
            throw error;
        }
    });

