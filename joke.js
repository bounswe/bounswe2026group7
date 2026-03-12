document.addEventListener("DOMContentLoaded", function () {
    const container = document.getElementById("joke-container");

    // Fetch a random joke from the official public API
    fetch("https://official-joke-api.appspot.com/random_joke")
        .then((response) => {
            if (!response.ok) {
                throw new Error("Network response was not ok");
            }
            return response.json();
        })
        .then((data) => {
            // Clear loading message
            container.innerHTML = "";

            // Create elements
            const setup = document.createElement("div");
            setup.className = "joke-setup";
            setup.textContent = data.setup;

            const punchline = document.createElement("div");
            punchline.className = "joke-punchline";
            punchline.textContent = data.punchline;

            // Append elements to container
            container.appendChild(setup);
            container.appendChild(punchline);
        })
        .catch((error) => {
            container.innerHTML = '<div class="error">Failed to load a joke. Please try again later.</div>';
            console.error("Error fetching joke:", error);
        });
});
