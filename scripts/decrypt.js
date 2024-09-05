const http = require('http');

// Function to fetch data from the given snapshot URL
function fetchSnapshotData(snapshotId) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: 'localhost',
            port: 9200,
            path: `/snapshots/${snapshotId}`,
            method: 'GET',
            headers: {
                'Accept': 'application/json',       // Accepting JSON response
                'Content-Type': 'application/json'  // Setting Content-Type
            }
        };

        const req = http.request(options, (res) => {
            let data = '';

            // Collect response data
            res.on('data', (chunk) => {
                data += chunk;
            });

            // Resolve the promise when the response ends
            res.on('end', () => {
                try {
                    if (data.trim().length === 0) {
                        throw new Error('Received empty response');
                    }

                    // Parse the received data
                    const jsonData = JSON.parse(data);
                    if (!jsonData.value) {
                        throw new Error('Unexpected JSON structure: no "value" field found');
                    }

                    // Decode relevant byte arrays in the response
                    const blocksBytes = jsonData.value.dataApplication.blocks;

                    // Decode the byte arrays into strings (if they are byte arrays)
                    let blocksDecoded;
                    if (Array.isArray(blocksBytes) && Array.isArray(blocksBytes[0]) && typeof blocksBytes[0][0] === 'number') {
                        const blocksString = String.fromCharCode(...blocksBytes[0]);
                        blocksDecoded = JSON.parse(blocksString);
                    } else {
                        blocksDecoded = blocksBytes;
                    }

                    // Add the decoded data back into jsonData for easier access
                    jsonData.value.dataApplication.blocksDecoded = blocksDecoded;

                    resolve(jsonData);
                } catch (e) {
                    reject(`Error parsing JSON for snapshot ${snapshotId}: ${e.message}`);
                }
            });
        });

        // Handle request errors
        req.on('error', (e) => {
            reject(e.message);
        });

        // End the request
        req.end();
    });
}

// Helper function to stringify objects with full expansion of nested objects
function expandObject(obj) {
    return JSON.stringify(obj, (key, value) => (typeof value === 'object' && value !== null ? value : value), 2);
}

// Function to process all snapshots between 1 and 100 and collect block data
async function processAllSnapshots() {
    let allBlocksData = [];

    for (let snapshotId = 1; snapshotId <= 10; snapshotId++) {
        try {
            const snapshotData = await fetchSnapshotData(snapshotId);

            // Extract the relevant blocks data
            const blocksData = {
                snapshotId: snapshotId,
                blocks: snapshotData.value.dataApplication.blocksDecoded // Extract the blocks decoded
            };

            allBlocksData.push(blocksData);
        } catch (error) {
            console.error(`Error processing snapshot ${snapshotId}: ${error}`);
        }
    }

    // Print all collected blocks data
    console.log('All Blocks Data:', expandObject(allBlocksData));
}

// Run the function to process all snapshots
processAllSnapshots();
