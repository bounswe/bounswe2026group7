/**
 * BUTTON CLICK HANDLERS
 *
 * Each button on the homepage has its own handler function below.
 * Replace the placeholder console.log with your own implementation.
 */

function onButton1Click() {
  console.log("Button 1 clicked -- implement me!");
}

function onButton2Click() {
  console.log("Button 2 clicked -- implement me!");
}

function onButton3Click() {
  console.log("Button 3 clicked: Fetching data from public API...");

  // Fetching a sample post from JSONPlaceholder API
  fetch('https://jsonplaceholder.typicode.com/posts/3')
    .then(response => {
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      return response.json();
    })
    .then(data => {
      console.log("Data received:", data);
      // Showing the result in an alert box as required for the task
      alert(
        "Individual Task - Button 3\n" +
        "--------------------------\n" +
        "Title: " + data.title + "\n" +
        "Body: " + data.body
      );
    })
    .catch(error => {
      console.error("Fetch error:", error);
      alert("Error: Could not retrieve data from API.");
    });
}

function onButton4Click() {
  console.log("Button 4 clicked -- implement me!");
}

function onButton5Click() {
  console.log("Button 5 clicked -- implement me!");
}
