const { dag4 } = require('@stardust-collective/dag4');
const axios = require('axios');

// Private key
const walletPrivateKey = '64ea71e28b8fb01673cf3d125809ec97181987bd65bea0bb5890bfe4a245762a';

// Dynamically get DAG address from private key
const getDAGAddressFromPrivateKey = (privateKey) => {
    const account = dag4.createAccount();
    account.loginPrivateKey(privateKey);
    return account.address;
};

// Create channel (sales channel) transaction
const buildCreateSalesChannelMessage = (address) => {
    return {
        CreateSalesChannel: {
            name: 'aba3',
            owner: address,
            station: "one",
            products: [
                ['Long', 5],
                ['Red', 10]
            ],
            startSnapshotOrdinal: 100,
            endSnapshotOrdinal: 10000
        }
    };
};

// AddSeller transaction
const buildAddSeller = (channelId, address) => {
    return {
        AddSeller: {
            channelId: channelId,
            address: address,
            seller: address
        }
    };
};

// Sale transaction
const buildCreateVoteMessage = (channelId, address) => {
    return {
        Sale: {
            channelId: channelId,
            address: address,
            station: "first station",
            sale: [
                ['Long', 2],
                ['Red', 3]
            ],
            payment: "Cash",
            timestamp: Date.now().toString()
        }
    };
};

// AddInventory transaction
const buildAddInventory = (channelId, address) => {
    return {
        AddInventory: {
            channelId: channelId,
            address: address,
            station: "first station",
            product: 'Long',
            amount: 20,
            timestamp: Date.now().toString()
        }
    };
};

// MoveInventory transaction
const buildMoveInventory = (channelId, address) => {
    return {
        MoveInventory: {
            channelId: channelId,
            address: address,
            toAddress: address,
            fromStation: "first station",
            toStation: 'second station',
            product: 'Long',
            amount: 10,
            timestamp: Date.now().toString()
        }
    };
};

// AddProducts transaction
const buildAddProduct = (channelId, address) => {
    return {
        AddProducts: {
            channelId: channelId,
            address: address,
            products: [["Coke", 5]]
        }
    };
};

// Generate proof for message signing
const generateProof = async (message, walletPrivateKey, account) => {
    const encodedMessage = Buffer.from(JSON.stringify(message)).toString('base64');
    const signature = await dag4.keyStore.dataSign(walletPrivateKey, encodedMessage);

    const publicKey = account.publicKey;
    const uncompressedPublicKey = publicKey.length === 128 ? '04' + publicKey : publicKey;

    return {
        id: uncompressedPublicKey.substring(2),
        signature
    };
};

// Send transaction and return the channelId from the response
const sendDataTransactionsUsingUrls = async (globalL0Url, metagraphL1DataUrl, message) => {
    const account = dag4.createAccount();
    account.loginPrivateKey(walletPrivateKey);

    account.connect({
        networkVersion: '2.0',
        l0Url: globalL0Url,
        testnet: true
    });

    // Generate the proof for this message
    const proof = await generateProof(message, walletPrivateKey, account);
    const body = {
        value: {
            ...message
        },
        proofs: [proof]
    };

    try {
        console.log(`Transaction body: ${JSON.stringify(body)}`);
        const response = await axios.post(`${metagraphL1DataUrl}/data`, body);
        console.log(`Response: ${JSON.stringify(response.data)}`);
        if (response.data && response.data.hash) {
            return response.data.hash; // Return the channelId (transaction hash)
        } else {
            console.error("No channel ID (hash) in the response!");
        }
    } catch (e) {
        console.error('Error sending transaction', e.message);
    }
    return null;
};

// Function to send all the transactions sequentially with the required 10-second delay
const sendSequentialTransactions = async () => {
    const globalL0Url = 'http://localhost:9000';
    const metagraphL1DataUrl = 'http://localhost:9400';

    // Get DAG address dynamically from private key
    const dagAddress = getDAGAddressFromPrivateKey(walletPrivateKey);

    // Send the first transaction (CreateSalesChannel) and retrieve the channelId from its response
    console.log('Sending CreateSalesChannel message...');
    const channelId = await sendDataTransactionsUsingUrls(globalL0Url, metagraphL1DataUrl, buildCreateSalesChannelMessage(dagAddress));

    if (!channelId) {
        console.error("Failed to create sales channel (channel). Aborting further transactions.");
        return;
    }

    console.log(`Sales channel (channel) created successfully with channelId: ${channelId}`);

    // Array of other messages that depend on the channelId
    await new Promise(resolve => setTimeout(resolve, 20000));
    const messages = [
        buildAddSeller(channelId, dagAddress),
        buildCreateVoteMessage(channelId, dagAddress),
        buildAddInventory(channelId, dagAddress),
        buildMoveInventory(channelId, dagAddress),
        buildAddProduct(channelId, dagAddress)
    ];

    // Loop through each message and send it with a 10-second delay
    for (let i = 0; i < messages.length; i++) {
        await sendDataTransactionsUsingUrls(globalL0Url, metagraphL1DataUrl, messages[i]);
        console.log(`Transaction ${i + 2} sent! Waiting for 10 seconds...`);
        await new Promise(resolve => setTimeout(resolve, 15000)); // Wait for 10 seconds
    }

    console.log('All transactions sent.');
};

// Start the sequential transaction process
sendSequentialTransactions();
