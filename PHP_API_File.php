<?php
// Enable errors temporarily for debugging (remove in production)
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

header('Content-Type: application/json');

// MySQL connection
$host = 'localhost';
$db   = 'yourdatabase';  // your remote DB
$db_user = 'yourdatabaseusername';
$db_pass = 'yourdatabasepassword';
$charset = 'utf8mb4';

$ds = "mysql:host=$host;dbname=$db;charset=$charset";
$opt = [
    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
];

try 
{
    $pd = new PDO($ds, $db_user, $db_pass, $opt);
} catch (PDOException $e) {
    echo json_encode(['error' => 'database connection failed: '.$e->getMessage()]);
    exit;
}

// Basic API key authentication
$API_KEY = "createstrongkey"; // change this to a strong key
$clientKey = $_GET['api_key'] ?? $_POST['api_key'] ?? '';
if ($clientKey !== $API_KEY) 
{
    echo json_encode(['error' => 'Unauthorized']);
    exit;
}

/* ---------------------- Existing actions remain here ------------------- */

// ... your get_users, add_user, update_user, etc. code stays unchanged ...

/* ---------------------- New SYNC action ------------------- */
if (isset($_POST['action']) && $_POST['action'] === 'sync_users_user1') {
    try {
        // Sync users → user1
        $pd->exec("
            INSERT INTO user1 (id, username, surname, email)
            SELECT id, username, surname, email
            FROM users
            ON DUPLICATE KEY UPDATE
                username=VALUES(username),
                surname=VALUES(surname),
                email=VALUES(email)
        ");

        // Sync user1 → users
        $pd->exec("
            INSERT INTO users (id, username, surname, email)
            SELECT id, username, surname, email
            FROM user1
            ON DUPLICATE KEY UPDATE
                username=VALUES(username),
                surname=VALUES(surname),
                email=VALUES(email)
        ");

        echo json_encode(['success' => true, 'message' => 'users and user1 synced successfully']);
    } catch (Exception $e) {
        echo json_encode(['error' => $e->getMessage()]);
    }
    exit;
}

// GET request: fetch all users
if (isset($_GET['action']) && $_GET['action'] === 'get_data') 
{
    $st = $pd->query('SELECT * FROM table_user'); // replace 'users' with your table name
    $row = $st->fetchAll();
    echo json_encode($rows);
    exit;
}

//fetch certain column
if (isset($_POST['action']) && $_POST['action'] === 'get_status') {
    $username = $_POST['username'] ?? '';

    if (empty($username)) {
        echo json_encode(['error' => 'Missing username']);
        exit;
    }

    $st = $pd->prepare('SELECT surname FROM table_user WHERE username = ?');
    $stmt->execute([$username]);
    $sur = $st->fetchColumn();

    if ($depart) {
        echo json_encode(['username' => $username, 'depart' => $sur]);
    } else {
        echo json_encode(['error' => 'No user found']);
    }
    exit;
}

// POST request: add a new user
if (isset($_POST['action']) && $_POST['action'] === 'add_data') 
{
    date_default_timezone_set("Africa/Maseru");
    $id = $_POST['id'] ?? 0;
    $username = $_POST['username'] ?? '';
    $surname = $_POST['surname'] ?? '';
    $email = $_POST['email'] ?? '';
    
    if ($id <= 0 || empty($username) || empty($surname) || empty($email)) 
    {
        echo json_encode(['error' => 'Missing parameters']);
        exit;
    }
    
    $now = date('Y-m-d H:i:s');
    $st = $pd->prepare('INSERT INTO table_user (id, username, surname, email, last_updated) VALUES (?, ?, ?, ?, ?)');
    $st->execute([$id, $username, $surname, $email, $now]);

    echo json_encode(['success' => true]);
    exit;
}



// Update user example
if (isset($_POST['action']) && $_POST['action'] === 'update_data') 
{
    date_default_timezone_set("Africa/Maseru");

    $id = $_POST['id'] ?? 0;
    $username = $_POST['username'] ?? '';
    $surname = $_POST['surname'] ?? '';
    $email = $_POST['email'] ?? '';

    if ($id <= 0 || empty($username) || empty($surname) || empty($email)) 
    {
        echo json_encode(['success' => false, 'error' => 'Missing parameters']);
        exit;
    }

    $now = date('Y-m-d H:i:s');
    $st = $pd->prepare('UPDATE users SET username=?, surname=?, email=?, last_updated=? WHERE id=?');
    $st->execute([$username, $surname, $email, $now, $id]);

    echo json_encode(['success' => true]);
    exit;
}


// DELETE user
if (isset($_POST['action']) && $_POST['action'] === 'delete_data') 
{
    $id = $_POST['id'] ?? 0;
    
    if ($id <= 0) 
    {
        echo json_encode(['error' => 'Invalid ID']);
        exit;
    }

    $st = $pd->prepare("DELETE FROM table_user WHERE id = ?");
    $st->execute([$id]);

    if ($stmt->rowCount() > 0) 
    {
        echo json_encode(['success' => true, 'deleted_id' => $id]);
    } 
    else 
    {
        echo json_encode(['error' => 'No user found with that ID']);
    }
    exit;
}

echo json_encode(['error' => 'Invalid request']);
?>