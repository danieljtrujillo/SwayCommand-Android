// Android addition: keep picked media inside the app across process restarts.
export async function storedFile(path, file) {
  const db = await new Promise((resolve, reject) => {
    const request = indexedDB.open('sway-android-media', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('files');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  try {
    return await new Promise((resolve, reject) => {
      const transaction = db.transaction('files', file ? 'readwrite' : 'readonly');
      const request = file ? transaction.objectStore('files').put(file, path) : transaction.objectStore('files').get(path);
      transaction.oncomplete = () => resolve(file || request.result);
      transaction.onerror = () => reject(transaction.error || new Error('Media storage failed'));
      transaction.onabort = () => reject(transaction.error || new Error('Media storage aborted'));
    });
  } finally { db.close(); }
}
