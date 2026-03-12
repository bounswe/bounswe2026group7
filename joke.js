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
            const setup = document.createElement("p");
            setup.style.fontWeight = "600";
            setup.style.color = "#334155";
            setup.textContent = data.setup;

            const punchline = document.createElement("p");
            punchline.style.fontWeight = "500";
            punchline.style.color = "#4f46e5";
            punchline.textContent = data.punchline;

            // Append elements to container
            container.appendChild(setup);
            container.appendChild(punchline);
        })
        .catch((error) => {
            container.innerHTML = '<p style="color: #ef4444;">Failed to load a joke. Please try again later.</p>';
            console.error("Error fetching joke:", error);
        });
});
