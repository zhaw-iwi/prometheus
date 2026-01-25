window.addEventListener("load", () => {
  updateTimestamp();
  setInterval(updateTimestamp, 30000);
});

function updateTimestamp() {
  const target = document.getElementById("last_updated");
  if (!target) {
    return;
  }
  const now = new Date();
  target.textContent = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}
