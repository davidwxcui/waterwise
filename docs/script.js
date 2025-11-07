// Smooth scrolling for navigation links
document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
  anchor.addEventListener("click", function (e) {
    e.preventDefault()
    const target = document.querySelector(this.getAttribute("href"))
    if (target) {
      target.scrollIntoView({
        behavior: "smooth",
        block: "start",
      })
    }
  })
})

// Animate stats on scroll
const observerOptions = {
  threshold: 0.5,
  rootMargin: "0px",
}

const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      const statNumbers = entry.target.querySelectorAll(".stat-number")
      statNumbers.forEach((stat) => {
        const finalValue = stat.textContent
        animateValue(stat, finalValue)
      })
    }
  })
}, observerOptions)

const statsSection = document.querySelector(".stats")
if (statsSection) {
  observer.observe(statsSection)
}

function animateValue(element, finalValue) {
  const isNumber = /^\d+K?\+?$/.test(finalValue)
  if (!isNumber) return

  const duration = 2000
  const start = 0
  const end = Number.parseInt(finalValue.replace(/\D/g, ""))
  const startTime = performance.now()

  function update(currentTime) {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    const current = Math.floor(progress * end)

    if (finalValue.includes("K")) {
      element.textContent = current + "K+"
    } else if (finalValue.includes("%")) {
      element.textContent = current + "%"
    } else if (finalValue.includes("★")) {
      element.textContent = (current / 10).toFixed(1) + "★"
    } else {
      element.textContent = current + "+"
    }

    if (progress < 1) {
      requestAnimationFrame(update)
    }
  }

  requestAnimationFrame(update)
}



// Mobile menu toggle
const mobileMenuBtn = document.getElementById("mobileMenuBtn")
const mobileMenu = document.getElementById("mobileMenu")

mobileMenuBtn?.addEventListener("click", () => {
  mobileMenu.classList.toggle("active")
  // Change icon
  mobileMenuBtn.textContent = mobileMenu.classList.contains("active") ? "✕" : "☰"
})

// Close menu when clicking on a link
document.querySelectorAll(".mobile-menu-links a").forEach((link) => {
  link.addEventListener("click", () => {
    mobileMenu.classList.remove("active")
    mobileMenuBtn.textContent = "☰"
  })
})

// Close menu when clicking outside
document.addEventListener("click", (e) => {
  if (!mobileMenu.contains(e.target) && !mobileMenuBtn.contains(e.target)) {
    mobileMenu.classList.remove("active")
    mobileMenuBtn.textContent = "☰"
  }
})

// Smooth scrolling for navigation links (existing code)
document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
  anchor.addEventListener("click", function (e) {
    e.preventDefault()
    const target = document.querySelector(this.getAttribute("href"))
    if (target) {
      target.scrollIntoView({
        behavior: "smooth",
        block: "start",
      })
    }
  })
})
