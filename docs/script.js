        // Smooth scrolling for navigation links
        document.querySelectorAll('a[href^="#"]').forEach(anchor => {
            anchor.addEventListener('click', function (e) {
                e.preventDefault();
                const target = document.querySelector(this.getAttribute('href'));
                if (target) {
                    target.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start'
                    });
                }
            });
        });

        // Animate stats on scroll
        const observerOptions = {
            threshold: 0.5,
            rootMargin: '0px'
        };

        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    const statNumbers = entry.target.querySelectorAll('.stat-number');
                    statNumbers.forEach(stat => {
                        const finalValue = stat.textContent;
                        animateValue(stat, finalValue);
                    });
                }
            });
        }, observerOptions);

        const statsSection = document.querySelector('.stats');
        if (statsSection) {
            observer.observe(statsSection);
        }

        function animateValue(element, finalValue) {
            const isNumber = /^\d+K?\+?$/.test(finalValue);
            if (!isNumber) return;
            
            const duration = 2000;
            const start = 0;
            const end = parseInt(finalValue.replace(/\D/g, ''));
            const startTime = performance.now();

            function update(currentTime) {
                const elapsed = currentTime - startTime;
                const progress = Math.min(elapsed / duration, 1);
                const current = Math.floor(progress * end);
                
                if (finalValue.includes('K')) {
                    element.textContent = current + 'K+';
                } else if (finalValue.includes('%')) {
                    element.textContent = current + '%';
                } else if (finalValue.includes('★')) {
                    element.textContent = (current / 10).toFixed(1) + '★';
                } else {
                    element.textContent = current + '+';
                }

                if (progress < 1) {
                    requestAnimationFrame(update);
                }
            }

            requestAnimationFrame(update);
        }

        // Mobile menu toggle
        const mobileMenuButton = document.querySelector('.mobile-menu-button');
        const navLinks = document.querySelector('.nav-links');
        
        mobileMenuButton?.addEventListener('click', () => {
            navLinks.style.display = navLinks.style.display === 'flex' ? 'none' : 'flex';
        });