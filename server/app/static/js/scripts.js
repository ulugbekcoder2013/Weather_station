// Mobile Burger Menu Toggle
document.addEventListener('DOMContentLoaded', () => {
    const burgerMenu = document.getElementById('burgerMenu');
    const mobileMenu = document.getElementById('mobileMenu');

    if (burgerMenu && mobileMenu) {
        burgerMenu.addEventListener('click', () => {
            mobileMenu.classList.toggle('hidden');
        });
    }

    // Flash notifications close button listener
    document.querySelectorAll('.notification').forEach((element) => {
        const closeBtn = element.querySelector('button');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                element.style.transition = 'opacity 0.5s ease-out';
                element.style.opacity = '0';
                setTimeout(() => element.remove(), 500);
            });
        }
    });
});



