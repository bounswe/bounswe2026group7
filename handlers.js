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
  console.log("Button 3 clicked -- implement me!");
}

function onButton4Click() {
  console.log("Button 4 clicked -- implement me!");
}

function onButton5Click() {
  console.log("Button 5 clicked -- implement me!");
}

function onButton6Click() {
  fetch("https://opentdb.com/api.php?amount=1&type=multiple")
    .then(function (response) {
      return response.json();
    })
    .then(function (data) {
      var q = data.results[0];
      var message =
        "Category: " + q.category + "\n\n" +
        "Question: " + q.question + "\n\n" +
        "Correct Answer: " + q.correct_answer;
      alert(message);
    })
    .catch(function (error) {
      alert("Failed to fetch trivia question. Please try again.");
    });
}