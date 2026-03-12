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
  // Fetch data from the highly stable Random User API
  fetch('https://randomuser.me/api/')
    .then(response => response.json())
    .then(data => {
      // Get the generated user data
      const user = data.results[0];
      
      // Open a new blank page
      const newWindow = window.open('', '_blank');
      
      // Write the description and the API response to the new page
      newWindow.document.write('<h2 style="font-family: sans-serif;">🌍 Random Global Citizen</h2>');
      newWindow.document.write('<p style="font-family: sans-serif;"><strong>Description:</strong> This public API returns a randomly generated user profile from different countries around the world.</p>');
      
      // Add their picture
      newWindow.document.write('<img src="' + user.picture.large + '" style="border-radius: 50%; border: 3px solid #483D8B; margin-top: 10px;">');
      
      // Add their info
      newWindow.document.write('<h3 style="font-family: sans-serif;">Name: ' + user.name.first + ' ' + user.name.last + '</h3>');
      newWindow.document.write('<ul style="font-family: sans-serif; font-size: 18px; color: #333;">');
      newWindow.document.write('<li><strong>Country:</strong> ' + user.location.country + '</li>');
      newWindow.document.write('<li><strong>City:</strong> ' + user.location.city + '</li>');
      newWindow.document.write('<li><strong>Timezone:</strong> ' + user.location.timezone.description + '</li>');
      newWindow.document.write('</ul>');
    })
    .catch(error => {
      console.error('Error fetching API:', error);
      alert('Failed to load global citizen data!');
    });
}